package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ChatReq;
import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.chapter.ChapterService;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.character.CharacterRepository;
import com.lingxi.character.NovelCharacter;
import com.lingxi.common.BizException;
import com.lingxi.memory.ChapterOutline;
import com.lingxi.memory.ChapterOutlineRepository;
import com.lingxi.memory.StoryMemory;
import com.lingxi.memory.StoryMemoryRepository;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.user.User;
import com.lingxi.world.WorldSetting;
import com.lingxi.world.WorldSettingRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AiService {

    private static final String SYSTEM_PROMPT = """
            你是「灵犀创作」平台内置的资深中文网文创作助手，文笔扎实、节奏感强，深谙网文爽点与期待感营造。请严格遵守：
            1. 使用简体中文写作，文风与用户提供的作品设定保持一致；
            2. 只输出创作内容本身，不要输出解释、前言、结语或自我评价；
            3. 尊重用户已有的人物设定、世界观与情节走向，不得擅自改动人名与既有事实；
            4. 输出纯文本，段落之间用空行分隔，不要使用 Markdown 语法。
            """;

    private final LlmClientProvider llmClientProvider;
    private final NovelService novelService;
    private final ChapterRepository chapterRepository;
    private final ChapterService chapterService;
    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final ChapterOutlineRepository chapterOutlineRepository;
    private final StoryMemoryRepository storyMemoryRepository;
    private final ContextBuilder contextBuilder;

    public AiService(LlmClientProvider llmClientProvider, NovelService novelService,
                     ChapterRepository chapterRepository, ChapterService chapterService,
                     CharacterRepository characterRepository,
                     WorldSettingRepository worldSettingRepository,
                     ChapterOutlineRepository chapterOutlineRepository, StoryMemoryRepository storyMemoryRepository,
                     ContextBuilder contextBuilder) {
        this.llmClientProvider = llmClientProvider;
        this.novelService = novelService;
        this.chapterRepository = chapterRepository;
        this.chapterService = chapterService;
        this.characterRepository = characterRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.chapterOutlineRepository = chapterOutlineRepository;
        this.storyMemoryRepository = storyMemoryRepository;
        this.contextBuilder = contextBuilder;
    }

    /** 当前用户的生效 LLM 客户端（用户级配置优先，全局配置兜底） */
    private LlmClient llm(User user) {
        return llmClientProvider.forUser(user);
    }

    public Flux<String> outline(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = baseContext(novel) + """

                【任务】
                请为这部小说生成一份全书大纲：
                1. 按“第X卷”分卷，每卷先给出一句核心冲突，再列 3-6 个关键章节的一句话梗概；
                2. 起承转合完整，卷末设置高潮；
                3. 结尾附一段“主题与卖点”。
                """;
        return llm(user).stream(AiTaskType.OUTLINE, contextOf(novel, null), SYSTEM_PROMPT, prompt);
    }

    public Flux<String> continueChapter(Long novelId, Long chapterId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = loadChapter(novelId, chapterId);
        // LIMIT 1 查询上一章尾部，避免整本书全量加载
        String prevTail = chapterRepository
                .findFirstByNovelIdAndDeletedFalseAndChapterOrderLessThanOrderByChapterOrderDescIdDesc(
                        novel.getId(), chapter.getChapterOrder())
                .map(c -> tail(c.getContent(), 800))
                .orElse("");
        String currentTail = tail(chapter.getContent(), 1200);

        StringBuilder prompt = new StringBuilder(baseContext(novel));
        if (!prevTail.isEmpty()) {
            prompt.append("\n【上一章结尾】\n").append(prevTail).append("\n");
        }
        prompt.append("\n【当前章节】第").append(chapter.getChapterOrder())
                .append("章 ").append(chapter.getTitle()).append("\n");
        if (currentTail.isEmpty()) {
            prompt.append("（本章尚未动笔，请直接开篇）");
        } else {
            prompt.append("\n【本章已写内容结尾】\n").append(currentTail);
        }
        prompt.append("""

                【任务】
                请从当前内容结束处自然续写 500-800 字，衔接上文语气与节奏，不要重复已有内容，不要进行总结。
                """);
        return llm(user).stream(AiTaskType.CONTINUE, contextOf(novel, null), SYSTEM_PROMPT, prompt.toString());
    }

    public Flux<String> polish(Long novelId, String text, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = baseContext(novel) + "\n【原文】\n" + tail(text, 4000) + """

                \n【任务】
                请对以上文字进行润色：提升文笔与画面感、优化句式节奏，保留原意与人称视角，直接输出润色后的全文。
                """;
        return llm(user).stream(AiTaskType.POLISH, contextOf(novel, text), SYSTEM_PROMPT, prompt);
    }

    /**
     * 自动创作流水线专用：为“待写章节”生成整章正文。
     * 自动流水线会先建好空章节再调用，因此这里统一按“已有章节、向尾部续写整章”处理。
     */
    public Flux<String> writeChapter(Long novelId, Long chapterId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        int targetWords = perChapterTargetWords(novel);
        Chapter chapter = loadChapter(novelId, chapterId);
        String prevTail = chapterRepository
                .findFirstByNovelIdAndDeletedFalseAndChapterOrderLessThanOrderByChapterOrderDescIdDesc(
                        novel.getId(), chapter.getChapterOrder())
                .map(c -> tail(c.getContent(), 800))
                .orElse("");
        String currentTail = tail(chapter.getContent(), 1200);

        StringBuilder prompt = new StringBuilder(baseContext(novel));
        if (!prevTail.isEmpty()) {
            prompt.append("\n【上一章结尾】\n").append(prevTail).append("\n");
        }
        prompt.append("\n【当前章节】第").append(chapter.getChapterOrder())
                .append("章 ").append(chapter.getTitle()).append("\n");
        if (currentTail.isEmpty()) {
            prompt.append("（本章尚未动笔，请直接开篇，注意与上一章情节自然衔接）");
        } else {
            prompt.append("\n【本章已写内容结尾】\n").append(currentTail);
        }
        // 注入本章大纲（若存在）：让 AI 严格按规划施工，避免跑偏、收尾失控
        chapterOutlineRepository.findByChapterId(chapter.getId()).ifPresent(o -> {
            if (o.getOutline() != null && !o.getOutline().isBlank()) {
                prompt.append("\n【本章大纲（请严格据此展开，确保要点全部覆盖、收尾呼应大纲）】\n")
                        .append(o.getOutline()).append("\n");
            }
        });
        prompt.append("""

                【任务】
                请为本章写出完整正文，目标字数约 %d 字：情节推进扎实、有场景描写与对话、结尾留钩子；直接输出正文，不要输出标题与解释。
                """.formatted(targetWords));
        return llm(user).stream(AiTaskType.WRITE_CHAPTER, contextOf(novel, chapter.getContent()), SYSTEM_PROMPT, prompt.toString());
    }

    /**
     * 按大纲生成整章正文并落库（覆盖式，但 ChapterService.save 会先为旧内容留版本快照，可回滚）。
     * 流式输出正文的同时在 onComplete 时整章覆盖写入：用于“章节大纲→正文”的一键生成。
     * 前端先用 Popconfirm 提示覆盖风险，再调用本接口。
     */
    public Flux<String> writeChapterAndSave(Long novelId, Long chapterId, User user) {
        Chapter chapter = loadChapter(novelId, chapterId);
        String title = chapter.getTitle();
        StringBuilder acc = new StringBuilder();
        return writeChapter(novelId, chapterId, user)
                .doOnNext(acc::append)
                .doOnComplete(() -> {
                    String content = acc.toString().trim();
                    if (!content.isEmpty()) {
                        chapterService.save(chapter.getId(), user, new SaveReq(title, content, "ai"));
                    }
                });
    }

    public Flux<String> expand(Long novelId, String text, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = baseContext(novel) + "\n【原文】\n" + tail(text, 4000) + """

                \n【任务】
                请在不偏离原意的前提下，将以上文字扩写至原来的 2-3 倍，补充细节描写、心理活动与环境烘托，直接输出扩写后的全文。
                """;
        return llm(user).stream(AiTaskType.EXPAND, contextOf(novel, text), SYSTEM_PROMPT, prompt);
    }

    public Flux<String> chat(Long novelId, ChatReq req, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        StringBuilder prompt = new StringBuilder(baseContext(novel));
        if (req.history() != null && !req.history().isEmpty()) {
            prompt.append("\n【近期对话】\n");
            req.history().stream().limit(10).forEach(m -> {
                String role = "assistant".equals(m.role()) ? "助手" : "用户";
                prompt.append(role).append("：").append(tail(m.content(), 500)).append("\n");
            });
        }
        prompt.append("\n【用户的新问题】\n").append(req.message().trim());
        prompt.append("""

                【任务】
                请围绕这部小说的创作问题进行回答，观点明确、建议具体可操作。
                """);
        return llm(user).stream(AiTaskType.CHAT, contextOf(novel, req.message()), SYSTEM_PROMPT, prompt.toString());
    }

    /**
     * 生成章节大纲并落库（每章一条，覆盖更新）。
     * 用于“创作前规划”：明确本章的场景节拍、关键进展、人物弧光与收尾钩子，指导后续正文创作。
     * 流式完成后持久化；中途取消/失败不落库。
     */
    public Flux<String> generateChapterOutline(Long novelId, Long chapterId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = loadChapter(novelId, chapterId);
        StringBuilder prompt = new StringBuilder(contextBuilder.buildNovelInfo(novel))
                .append("\n【当前章节】第").append(chapter.getChapterOrder())
                .append("章《").append(chapter.getTitle()).append("》\n");
        String content = chapter.getContent();
        if (content != null && !content.isBlank()) {
            prompt.append("（本章已有部分草稿，可据此提炼更贴合的规划；若尚未动笔则自由规划）\n");
            prompt.append("【本章已有内容】\n").append(tail(content, 2000)).append("\n");
        }
        prompt.append("""

                【任务】
                请为这一章撰写“写作大纲”（规划，而非正文）：
                1. 用 3-6 个要点列出本章的场景节拍与关键情节推进；
                2. 点明本章要完成的人物状态变化或关系进展；
                3. 标出本章结尾的钩子（为下一章留悬念或动力）；
                4. 确保与全书大纲的方向一致，避免偏离主线、尾大不掉。
                只输出大纲要点，不要写正文，不要输出标题、序号以外的解释。
                """);
        StringBuilder acc = new StringBuilder();
        return llm(user).stream(AiTaskType.CHAPTER_OUTLINE, contextOf(novel, content), SYSTEM_PROMPT, prompt.toString())
                .doOnNext(acc::append)
                .doOnComplete(() -> saveOutline(novel.getId(), chapter.getId(), acc.toString().trim()));
    }

    /** 按优先级打包的静态上下文：作品信息 + 一致性卡片 + 大纲 + 故事脑 + 已规划章节大纲 */
    private String baseContext(Novel novel) {
        List<NovelCharacter> characters = characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        List<WorldSetting> worlds = worldSettingRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        StoryMemory memory = storyMemoryRepository.findByNovelId(novel.getId()).orElse(null);
        List<ChapterOutline> outlines = chapterOutlineRepository.findByNovelIdOrdered(novel.getId());
        return contextBuilder.buildBase(novel, characters, worlds, memory, outlines);
    }

    /**
     * 每章目标字数：优先按“总目标字数 / 章节数”动态均摊；作品未设置总目标时退回兜底值。
     * 这是创作时的唯一字数依据——前端不传、也不让用户手动选择字数。
     */
    private static final int FALLBACK_CHAPTER_WORDS = 1500;

    private int perChapterTargetWords(Novel novel) {
        Integer total = novel.getTargetWords();
        long count = chapterRepository.countByNovelIdAndDeletedFalse(novel.getId());
        if (total != null && total > 0 && count > 0) {
            return (int) Math.ceil((double) total / count);
        }
        return FALLBACK_CHAPTER_WORDS;
    }

    private Chapter loadChapter(Long novelId, Long chapterId) {
        return chapterRepository.findById(chapterId)
                .filter(c -> c.getNovelId().equals(novelId) && !c.isDeleted())
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
    }

    private void saveOutline(Long novelId, Long chapterId, String outline) {
        if (outline == null || outline.isBlank()) {
            return;
        }
        ChapterOutline o = chapterOutlineRepository.findByChapterId(chapterId).orElseGet(() -> {
            ChapterOutline co = new ChapterOutline();
            co.setNovelId(novelId);
            co.setChapterId(chapterId);
            return co;
        });
        o.setOutline(outline);
        chapterOutlineRepository.save(o);
    }

    private AiContext contextOf(Novel novel, String sourceText) {
        List<String> names = characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId())
                .stream().map(NovelCharacter::getName).toList();
        return new AiContext(novel.getTitle(), novel.getGenre(), names, sourceText);
    }

    private static String tail(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(s.length() - max);
    }
}
