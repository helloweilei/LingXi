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

    /**
     * 生成封面提示词：依据书名、题材、简介、人物与全书大纲，产出一段可直接用于
     * AI 绘图（Midjourney / Stable Diffusion 等）的中文封面提示词。
     * 结果供前端展示与编辑，用户确认后再保存到 novel.coverPrompt。
     *
     * @param authorName 作者名（可选）。这是用户在各发布平台实际使用的署名，
     *                   会原样写入提示词并要求 AI 逐字呈现、禁止改写或虚构。
     */
    public Flux<String> coverPrompt(Long novelId, String authorName, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        List<NovelCharacter> characters = characterRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        List<WorldSetting> worlds = worldSettingRepository.findByNovelIdOrderByCreatedAtAscIdAsc(novel.getId());
        StringBuilder prompt = new StringBuilder(contextBuilder.buildNovelInfo(novel))
                .append("\n\n").append(contextBuilder.buildConsistencyCard(characters, worlds));
        String outline = novel.getOutline();
        if (outline != null && !outline.isBlank()) {
            prompt.append("\n【全书大纲（用于提炼封面气质与主线意象）】\n")
                    .append(tail(outline, 1500)).append("\n");
        }
        prompt.append("\n【文风要求】\n").append(styleDirective(novel));
        prompt.append("""

                【任务】
                请为这部小说写一段“封面绘图提示词”，要求可直接投喂给 AI 绘图模型。
                封面必须包含书名文字本身（即在画面中直接排版书名「%s」），因此输出需包含两部分：
                第一部分【主视觉】——画面要素：
                1. 先用一行给出封面主视觉方案（主体 + 场景 + 氛围），一句话概括；
                2. 再分行列出主体、构图、色彩与光线、材质质感、象征性细节；
                3. 贴合本书题材与文风，画面有辨识度，不要泛泛而谈；
                4. 主体与文字必须错开排布，文字不得压在人脸或主体之上，需保证可读性。
                第二部分【书名与作者名排版】——明确写出：
                5. 书名原文「%s」，需在画面中清晰、完整、正确地呈现，不得改写、漏字或用乱码替代；
                6. 书名的字体气质（如书法体/黑体/手写体）、字号、颜色与描边或阴影处理；
                7. 书名在画面中的具体位置（常见为上方或下方居中），与背景的对比度要足够。
                """.formatted(novel.getTitle(), novel.getTitle()));
        String cleanAuthor = authorName == null ? "" : authorName.trim();
        if (cleanAuthor.isEmpty()) {
            prompt.append("""
                    8. 作者名区域：封面需预留位置给作者名（本次用户未指定作者名），说明该区域的位置与排版建议；
                       严禁自行杜撰任何作者名或笔名。
                    """);
        } else {
            prompt.append("""
                    8. 作者名必须严格使用「%s」——这是该作者在各发布平台实际使用的署名，
                       属于不可改动的既定事实：
                       - 必须逐字原样呈现，严禁自行编造、替换、改写、增删或"优化"该名字；
                       - 严禁虚构其他作者名/笔名，也不要使用"作者名"、"XXX 著"等占位符替代；
                       - 位置通常在书名下方或封面底部，字号需明显小于书名，颜色与字体气质要与书名协调；
                       - 作者名不得与书名重叠，也不得被画面主体遮挡，确保清晰可读。
                    """.formatted(cleanAuthor));
        }
        prompt.append("""
                最后单起一行输出负面提示词（负面提示词：…），需排除：文字乱码、错别字、变形文字、重复文字、
                错误或虚构的作者名、水印、平台 logo、拼贴感、廉价感等会影响成图可用性的元素。
                只输出提示词正文，不要解释、不要标题层级以外的附加说明。
                """);
        return llm(user).stream(AiTaskType.COVER_PROMPT, contextOf(novel, null), SYSTEM_PROMPT, prompt.toString());
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
     *
     * @param totalChapters 全书总章节数（用于判断本章是否为终章）；为 null 时按已存在章节数推断
     */
    public Flux<String> writeChapter(Long novelId, Long chapterId, User user, Integer totalChapters) {
        Novel novel = novelService.loadOwned(novelId, user);
        int targetWords = perChapterTargetWords(novel);
        Chapter chapter = loadChapter(novelId, chapterId);
        String prevTail = chapterRepository
                .findFirstByNovelIdAndDeletedFalseAndChapterOrderLessThanOrderByChapterOrderDescIdDesc(
                        novel.getId(), chapter.getChapterOrder())
                .map(c -> tail(c.getContent(), 800))
                .orElse("");
        String currentTail = tail(chapter.getContent(), 1200);

        // 全书章节总数：显式传入优先，否则取当前已有章节数
        long total = totalChapters != null && totalChapters > 0
                ? totalChapters
                : chapterRepository.countByNovelIdAndDeletedFalse(novel.getId());
        boolean isFinalChapter = total > 0 && chapter.getChapterOrder() >= total;

        StringBuilder prompt = new StringBuilder(baseContext(novel));
        if (!prevTail.isEmpty()) {
            prompt.append("\n【上一章结尾】\n").append(prevTail).append("\n");
        }
        prompt.append("\n【当前章节】第").append(chapter.getChapterOrder())
                .append("章 ").append(chapter.getTitle());
        if (total > 0) {
            prompt.append("（全书共 ").append(total).append(" 章，本章是第 ")
                    .append(chapter.getChapterOrder()).append(" 章");
            prompt.append(isFinalChapter ? "，且是全书最后一章）" : "）");
        }
        prompt.append("\n");
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
        prompt.append("\n【文风要求】\n").append(styleDirective(novel));
        prompt.append(isFinalChapter ? """

                【任务】这是全书的最后一章，请写出完整正文并收束全书。
                1. 本章必须完整收尾：主要矛盾得到交代，主角的处境与心态有明确落点，全书主线与核心悬念给出答案；
                2. 不要突然结束或草草了事，回顾并呼应前文埋下的伏笔与人物成长；
                3. 收束不等于仓促最后一页，要让读者感到“故事完整地结束了”，而非“被截断了”。
                """ : """

                【任务】请写出本章完整正文，要求本章内部形成完整的叙事闭环：
                1. 开篇承接上文（若已有内容则从当前结尾自然续写），交代本章处境与目标；
                2. 中段围绕目标展开冲突、场景与对话，情节要有实质推进；
                3. 结尾必须交代本章主要矛盾的阶段性结果（解决、失败或转折），使本章情节闭合；
                4. 在情节闭合之后，可以留一个自然的悬念或期待引向下一章，但不得用“未写完的片段”充当结尾。
                """);
        prompt.append("""

                【字数】
                本章不少于 %d 字。请先把情节写完整、写闭合，再考虑字数；宁可略超也不要为了凑字数而仓促收尾或半途断掉。
                """.formatted(targetWords));
        prompt.append("\n只输出正文，不要输出标题、序号、解释或字数说明。");
        return llm(user).stream(AiTaskType.WRITE_CHAPTER, contextOf(novel, chapter.getContent()), SYSTEM_PROMPT, prompt.toString());
    }

    /**
     * 按大纲生成整章正文并落库（覆盖式，但 ChapterService.save 会先为旧内容留版本快照，可回滚）。
     * 流式输出正文的同时在 onComplete 时整章覆盖写入：用于“章节大纲→正文”的一键生成。
     * 前端先用 Popconfirm 提示覆盖风险，再调用本接口。
     */
    public Flux<String> writeChapterAndSave(Long novelId, Long chapterId, User user, Integer totalChapters) {
        Chapter chapter = loadChapter(novelId, chapterId);
        String title = chapter.getTitle();
        StringBuilder acc = new StringBuilder();
        return writeChapter(novelId, chapterId, user, totalChapters)
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
     * 同时让 AI 顺带产出一个有信息量的章节名——用户已命名（或有正文）时不覆盖。
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
        prompt.append("\n【文风要求】\n").append(styleDirective(novel));
        prompt.append("""

                【任务】
                请为这一章输出“章节名”与“写作大纲”，严格按以下两段格式输出：

                章节名：<6-16字的章节标题>

                大纲：
                1. 用 3-6 个要点列出本章的场景节拍与关键情节推进；
                2. 点明本章要完成的人物状态变化或关系进展；
                3. 明确写出“本章主要矛盾的阶段性结果”（如何解决 / 失败 / 反转）——本章情节必须在此闭合，不能悬而未决；
                4. 在上述结果之后，再为下一章留一个自然的悬念或期待（钩子），不得用钩子代替本章的收束；
                5. 确保与全书大纲的方向一致，避免偏离主线、尾大不掉。

                【章节名要求】
                - 必须是**具体、贴合本章核心事件**的标题（如「雨夜惊变」「她终于开了口」「F级异能的代价」），
                  要能让读者一眼看出本章发生了什么，禁止使用「未命名章节」「第X章」「待定」这类占位名；
                - 长度 6-16 字，不要加书名号、标点结尾或序号（如「第X章」）；
                - 贴合本书语言风格，不要使用其他章节已用过的标题。

                只输出上述格式内容，不要写正文，不要额外解释。
                """);
        StringBuilder acc = new StringBuilder();
        return llm(user).stream(AiTaskType.CHAPTER_OUTLINE, contextOf(novel, content), SYSTEM_PROMPT, prompt.toString())
                .doOnNext(acc::append)
                .doOnComplete(() -> persistOutlineAndTitle(novel, chapter, acc.toString().trim(), user));
    }

    /**
     * 解析 AI 输出（章节名 + 大纲）并分别落库。
     * 解析失败时按纯大纲处理（兼容旧格式），不影响大纲功能。
     */
    private void persistOutlineAndTitle(Novel novel, Chapter chapter, String raw, User user) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String title = null;
        String outline = raw;
        java.util.regex.Matcher m = TITLE_PATTERN.matcher(raw);
        if (m.find()) {
            title = normalizeTitle(m.group(1));
            outline = raw.substring(m.end()).trim();
        }
        // 大纲正文里可能带上 "大纲：" 这行标签
        outline = stripLeadingLabel(outline, "大纲");
        saveOutline(novel.getId(), chapter.getId(), outline);
        // 仅在用户尚未命名、且本章还没有正文时才自动命名，避免覆盖用户已定的名字
        if (title != null && isPlaceholderTitle(chapter.getTitle()) && isBlank(chapter.getContent())) {
            chapterService.save(chapter.getId(), user, new SaveReq(title, null, "ai-title"));
        }
    }

    /** 章节名输出行：`章节名：xxx` */
    private static final java.util.regex.Pattern TITLE_PATTERN =
            java.util.regex.Pattern.compile("^\\s*(?:章节名|标题)\\s*[:：]\\s*(.+?)\\s*$", java.util.regex.Pattern.MULTILINE);

    /** 去除标题中的书名号/序号/尾部标点，并做长度兜底 */
    static String normalizeTitle(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("[《》「」『』\"'“”‘’]", "")
                .replaceAll("^第\\s*\\d+\\s*章[：:、.\\s]*", "")
                .replaceAll("[。．.，,、；;：:\\s]+$", "")
                .trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    /** 判断是否为无信息量的占位章节名（这些才允许被AI 命名覆盖） */
    static boolean isPlaceholderTitle(String t) {
        if (isBlank(t)) {
            return true;
        }
        String s = t.trim();
        if ("未命名章节".equals(s) || "未命名".equals(s) || "待定".equals(s)) {
            return true;
        }
        // 形如「第1章」「第 1 章」「1」「第一章」的纯序号名
        return s.matches("^第?\\s*\\d+\\s*[章回节卷]?\\s*[:：、.\\s]*$");
    }

    private static String stripLeadingLabel(String text, String label) {
        String t = text.stripLeading();
        String prefix = label + "：";
        String prefix2 = label + ":";
        if (t.startsWith(prefix) || t.startsWith(prefix2)) {
            return t.substring(t.startsWith(prefix) ? prefix.length() : prefix2.length()).stripLeading();
        }
        return t;
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
     * 为单章生成一个有信息量的章节名（供「章节」页对未命名章节使用）。
     * 优先参考该章已有大纲，其次参考已写正文，最后回退到全书上下文。
     */
    public Flux<String> chapterTitle(Long novelId, Long chapterId, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        Chapter chapter = loadChapter(novelId, chapterId);
        StringBuilder prompt = new StringBuilder(baseContext(novel))
                .append("\n\n【当前章节】第").append(chapter.getChapterOrder()).append("章");
        String existingTitle = chapter.getTitle();
        if (!isPlaceholderTitle(existingTitle)) {
            prompt.append("（当前章节名为「").append(existingTitle).append("」，请给出一个更贴切的备选）\n");
        } else {
            prompt.append("（当前章节尚未命名）\n");
        }
        chapterOutlineRepository.findByChapterId(chapter.getId()).ifPresent(o -> {
            if (!isBlank(o.getOutline())) {
                prompt.append("【本章已有写作大纲（标题须贴合这些要点）】\n")
                        .append(tail(o.getOutline(), 1500)).append("\n");
            }
        });
        String content = chapter.getContent();
        if (!isBlank(content)) {
            prompt.append("【本章已写内容（标题须贴合开头情节）】\n").append(tail(content, 1500)).append("\n");
        }
        prompt.append("\n【文风要求】\n").append(styleDirective(novel));
        prompt.append("""

                【任务】
                请为这一章起一个标题，只输出标题本身，不要任何解释、引号或标点结尾。
                要求：
                1. 具体、贴合本章核心事件，能让读者一眼看出本章发生了什么（如「雨夜惊变」「她终于开了口」「F级异能的代价」）；
                2. 严禁使用「未命名章节」「待定」「第X章」「本章」等占位名或序号名；
                3. 长度 6-16 字，不加书名号与序号前缀；
                4. 贴合本书语言风格，不要与其他章节标题重复。
                """);
        // 章节名短，流式输出即可；调用方取出全文后清洗
        return llm(user).stream(AiTaskType.CHAPTER_TITLE, contextOf(novel, null), SYSTEM_PROMPT, prompt.toString());
    }

    /**
     * 文风指令：把用户设置的语言风格（Novel.languageStyle）从“作品信息里的一行描述”
     * 升级为写作任务的强制约束；未设置时给出网文通用基线，避免风格漂移。
     */
    private String styleDirective(Novel novel) {
        String style = novel.getLanguageStyle();
        StringBuilder sb = new StringBuilder();
        if (style == null || style.isBlank()) {
            sb.append("1. 使用规范流畅的简体中文网文语感，语句凝练、节奏明快，不堆砌辞藻；");
        } else {
            sb.append("1. 严格遵循本书设定的语言风格「").append(style.trim()).append("」：");
            sb.append("用词、句式、叙述视角与整体调性都要贴合该风格，且全书保持一致，不得中途漂移；");
        }
        sb.append("\n2. 以场景、动作、对话与感官细节推动叙事，避免叙述者直接讲道理或空泛抒情；");
        sb.append("\n3. 保持情节连贯：人物动机、称谓、时间线与前文一致，不出现前后矛盾；");
        sb.append("\n4. 结尾必须写完整、写闭合，不留半句话或未完成的场景。");
        return sb.toString();
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
