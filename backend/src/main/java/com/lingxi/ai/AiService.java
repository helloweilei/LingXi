package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ChatReq;
import com.lingxi.chapter.Chapter;
import com.lingxi.chapter.ChapterRepository;
import com.lingxi.character.CharacterRepository;
import com.lingxi.character.NovelCharacter;
import com.lingxi.common.BizException;
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

    private final LlmClient llmClient;
    private final NovelService novelService;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;

    public AiService(LlmClient llmClient, NovelService novelService,
                     ChapterRepository chapterRepository, CharacterRepository characterRepository,
                     WorldSettingRepository worldSettingRepository) {
        this.llmClient = llmClient;
        this.novelService = novelService;
        this.chapterRepository = chapterRepository;
        this.characterRepository = characterRepository;
        this.worldSettingRepository = worldSettingRepository;
    }

    public Flux<String> outline(Long novelId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = buildSettings(novel) + """

                【任务】
                请为这部小说生成一份全书大纲：
                1. 按“第X卷”分卷，每卷先给出一句核心冲突，再列 3-6 个关键章节的一句话梗概；
                2. 起承转合完整，卷末设置高潮；
                3. 结尾附一段“主题与卖点”。
                """;
        return llmClient.stream(AiTaskType.OUTLINE, contextOf(novel, null), SYSTEM_PROMPT, prompt);
    }

    public Flux<String> continueChapter(Long novelId, Long chapterId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
        if (!chapter.getNovelId().equals(novel.getId())) {
            throw new BizException(404, "章节不存在", 404);
        }
        String prevTail = chapterRepository.findByNovelIdOrderByChapterOrderAscIdAsc(novel.getId()).stream()
                .filter(c -> c.getChapterOrder() < chapter.getChapterOrder())
                .reduce((first, second) -> second)
                .map(c -> tail(c.getContent(), 800))
                .orElse("");
        String currentTail = tail(chapter.getContent(), 1200);

        StringBuilder prompt = new StringBuilder(buildSettings(novel));
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
        return llmClient.stream(AiTaskType.CONTINUE, contextOf(novel, null), SYSTEM_PROMPT, prompt.toString());
    }

    public Flux<String> polish(Long novelId, String text, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = buildSettings(novel) + "\n【原文】\n" + tail(text, 4000) + """

                \n【任务】
                请对以上文字进行润色：提升文笔与画面感、优化句式节奏，保留原意与人称视角，直接输出润色后的全文。
                """;
        return llmClient.stream(AiTaskType.POLISH, contextOf(novel, text), SYSTEM_PROMPT, prompt);
    }

    public Flux<String> expand(Long novelId, String text, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = buildSettings(novel) + "\n【原文】\n" + tail(text, 4000) + """

                \n【任务】
                请在不偏离原意的前提下，将以上文字扩写至原来的 2-3 倍，补充细节描写、心理活动与环境烘托，直接输出扩写后的全文。
                """;
        return llmClient.stream(AiTaskType.EXPAND, contextOf(novel, text), SYSTEM_PROMPT, prompt);
    }

    public Flux<String> chat(Long novelId, ChatReq req, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        StringBuilder prompt = new StringBuilder(buildSettings(novel));
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
        return llmClient.stream(AiTaskType.CHAT, contextOf(novel, req.message()), SYSTEM_PROMPT, prompt.toString());
    }

    /** 汇总书名/类型/简介/大纲/角色/世界观，作为所有任务的共享上下文 */
    private String buildSettings(Novel novel) {
        StringBuilder sb = new StringBuilder("【作品信息】\n书名：").append(novel.getTitle())
                .append("\n类型：").append(orDefault(novel.getGenre(), "未设置"))
                .append("\n简介：").append(orDefault(novel.getDescription(), "（暂无）")).append("\n");
        if (novel.getOutline() != null && !novel.getOutline().isBlank()) {
            sb.append("\n【全书大纲】\n").append(tail(novel.getOutline(), 3000)).append("\n");
        }
        List<NovelCharacter> characters = characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        if (!characters.isEmpty()) {
            sb.append("\n【角色设定】\n");
            for (NovelCharacter c : characters) {
                sb.append("- ").append(c.getName());
                if (c.getRole() != null && !c.getRole().isBlank()) {
                    sb.append("（").append(c.getRole()).append("）");
                }
                if (c.getDescription() != null && !c.getDescription().isBlank()) {
                    sb.append("：").append(tail(c.getDescription(), 300));
                }
                sb.append("\n");
            }
        }
        List<WorldSetting> worlds = worldSettingRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        if (!worlds.isEmpty()) {
            sb.append("\n【世界观设定】\n");
            for (WorldSetting w : worlds) {
                sb.append("- ").append(w.getTitle()).append("：")
                        .append(tail(orDefault(w.getContent(), ""), 500)).append("\n");
            }
        }
        return sb.toString();
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

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
