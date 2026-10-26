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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

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
     * 去AI化：在**不改变语义**的前提下改写"AI腔"的句子，让行文更自然、更有"人味"。
     * 与润色的分工：润色提升文笔与画面感；去AI化专攻"机器味"——套话、排比、解说腔、翻译腔、情绪贴标签。
     */
    public Flux<String> deAi(Long novelId, String text, User user) {
        Novel novel = novelService.loadOwned(novelId, user);
        String prompt = baseContext(novel) + "\n【原文】\n" + tail(text, 4000) + """

                \n【任务】
                请把以上文字改写成"真人写的小说"：语气自然、有细节、有呼吸感，读起来不像 AI 生成的。

                【必须遵守】
                1. 不改变任何语义：情节事实、人物行为、对话内容、信息量、时间顺序全部保持原样——只改"怎么说"，不改"说了什么"；
                2. 保留人称视角，并遵循本书语言风格；
                3. 对话内容原则上保留原意（可把极不自然的口语句式改顺，但不得改变台词含义与潜台词）。

                【重点消除这些"AI腔"】
                1. 空洞的总结句与解说腔（如"这一刻，他终于明白了……""这不仅仅是一场较量，更是一次蜕变"）；
                2. 排比堆砌、四字成语连用、副词滥用（"缓缓地""轻轻地""深深地"满屏飞）；
                3. 翻译腔与欧化长句（"他的眼睛深处闪过了一丝难以捉摸的情绪"式从句套从句）；
                4. 情绪直说、给情绪贴标签（把"他很紧张"改成具体动作与生理反应；反之把堆砌的细节收拢成一句有力的白描）；
                5. 机械过渡词（"与此同时""值得注意的是""总而言之""毋庸置疑"）；
                6. 比喻滥用与"仿佛/似乎"疲劳，华而不实的环境描写。

                【改写方向】
                - 长短句交错，该断就断，别一口气说到底；
                - 用动作、感官、细节呈现情绪，而不是给情绪贴标签；
                - 保留网络小说该有的爽感与画面感，口语自然但不水。

                只输出改写后的全文，不要解释，不要重复原文。
                """;
        return llm(user).stream(AiTaskType.DEAI, contextOf(novel, text), SYSTEM_PROMPT, prompt);
    }

    /**
     * 生成整章正文（写前规划 → 按本章大纲创作）。
     *
     * 语义澄清（重要）：
     * - 默认（appendExisting=false）：**整章重写**，忽略本章已有正文，从上一章结尾重新开始写。
     * 「生成正文」按钮走这条——用户点它的意图是"重新生成这一章"，而不是"接着上次继续"。
     * - appendExisting=true：把本章已有内容的尾部作为上下文，让 AI 从断点**续写**（只增不重）。
     * 「从当前内容接着写」入口走这条。
     *
     * @param totalChapters 全书总章节数（用于判断本章是否为终章）；为 null 时按已存在章节数推断
     */
    public Flux<String> writeChapter(Long novelId, Long chapterId, User user, Integer totalChapters,
            boolean appendExisting) {
        Novel novel = novelService.loadOwned(novelId, user);
        int targetWords = perChapterTargetWords(novel, chapterId);
        Chapter chapter = loadChapter(novelId, chapterId);

        // 连贯性上下文：上一章的正文尾部 + 上一章大纲 + 前若干章大纲概览
        Chapter prevChapter = chapterRepository
                .findFirstByNovelIdAndDeletedFalseAndChapterOrderLessThanOrderByChapterOrderDescIdDesc(
                        novel.getId(), chapter.getChapterOrder())
                .orElse(null);
        String prevTail = prevChapter == null ? "" : tail(prevChapter.getContent(), PREV_TAIL_CHARS);
        String prevTitle = prevChapter == null ? null : prevChapter.getTitle();
        // 仅"续写"模式才带入本章已有内容；重写模式必须忽略它，否则会从旧稿接着写
        String currentTail = appendExisting ? tail(chapter.getContent(), 1200) : "";

        // 全书章节总数：显式传入优先；否则以「规划条数」为准（大纲先行，章节由规划落地）
        long planned = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novel.getId()).size();
        long total = totalChapters != null && totalChapters > 0
                ? totalChapters
                : (planned > 0 ? planned : chapterRepository.countByNovelIdAndDeletedFalse(novel.getId()));
        boolean isFinalChapter = total > 0 && chapter.getChapterOrder() >= total;

        StringBuilder prompt = new StringBuilder(baseContext(novel));
        ChapterOutline prevOutline = prevChapter == null ? null
                : findOutlineByOrder(novel.getId(), prevChapter.getChapterOrder());
        if (prevChapter != null) {
            prompt.append("\n\n【上一章】第").append(prevChapter.getChapterOrder()).append("章");
            if (prevTitle != null && !prevTitle.isBlank()) {
                prompt.append("《").append(prevTitle).append("》");
            }
            prompt.append("\n");
            // 上一章大纲：让 AI 知道上一章整体发生了什么，而不只是末尾几百字
            if (prevOutline != null && !isBlank(prevOutline.getOutline())) {
                prompt.append("【上一章大纲（上一章完整发生了什么）】\n")
                        .append(tail(prevOutline.getOutline(), 900)).append("\n");
            }
            prompt.append("【上一章结尾原文（务必从这段之后无缝衔接，不要另起炉灶）】\n")
                    .append(prevTail).append("\n");
        } else {
            prompt.append("\n\n（这是全书第一章，没有上一章可承接）");
        }
        // 前几章大纲概览：维持长线主线与人物状态的一致性
        appendRecentOutlinesBrief(prompt, novel.getId(), chapter.getChapterOrder());

        prompt.append("\n\n【本章】第").append(chapter.getChapterOrder())
                .append("章 ").append(chapter.getTitle());
        if (total > 0) {
            prompt.append("（全书共 ").append(total).append(" 章");
            prompt.append(isFinalChapter ? "，且是全书最后一章）" : "）");
        }
        prompt.append("\n");
        if (currentTail.isEmpty()) {
            prompt.append("（本章尚未动笔，请直接开篇）");
        } else {
            prompt.append("\n【本章已写到的内容（请从这段之后接着写，不要重复已写部分）】\n")
                    .append(currentTail);
        }
        // 注入本章大纲（若存在）：让 AI 严格按规划施工，避免跑偏、收尾失控
        ChapterOutline curOutline = findOutlineByOrder(novel.getId(), chapter.getChapterOrder());
        if (curOutline != null && !isBlank(curOutline.getOutline())) {
            prompt.append("\n【本章大纲（请严格据此展开，确保要点全部覆盖、收尾呼应大纲）】\n")
                    .append(curOutline.getOutline()).append("\n");
        }
        prompt.append("\n【文风要求】\n").append(styleDirective(novel));
        prompt.append(isFinalChapter ? """

                【任务】这是全书的最后一章，请写出完整正文并收束全书。
                0. **【最高优先级】开篇必须与上一章结尾无缝衔接**：第一段要从上一章结尾的时点、地点、人物状态直接接续，不要重新开场；
                1. 本章必须完整收尾：主要矛盾得到交代，主角的处境与心态有明确落点，全书主线与核心悬念给出答案；
                2. 不要突然结束或草草了事，回顾并呼应前文埋下的伏笔与人物成长；
                3. 收束不等于仓促最后一页，要让读者感到"故事完整地结束了"，而非"被截断了"。
                """ : """

                【任务】请写出本章完整正文，要求本章内部形成完整的叙事闭环：
                0. **【最高优先级】开篇必须与上一章结尾无缝衔接**：
                   - 第一段要从上一章结尾的时点、地点、人物状态直接接续，不要重新开场、不要另起炉灶；
                   - 承接上一章留下的冲突、悬念或未完成的动作，不得当作新故事另写；
                   - 人物的位置、情绪、随身物品与上一章结尾保持一致。
                1. 交代本章处境与目标；
                2. 中段围绕目标展开冲突、场景与对话，情节要有实质推进；
                3. 结尾必须交代本章主要矛盾的阶段性结果（解决、失败或转折），使本章情节闭合；
                4. 在情节闭合之后，可以留一个自然的悬念或期待引向下一章，但不得用“未写完的片段”充当结尾。
                """);
        prompt.append("""

                【字数】
                本章不少于 %d 字。请先把情节写完整、写闭合，再考虑字数；宁可略超也不要为了凑字数而仓促收尾或半途断掉。
                本章必须**一次性写完并写完整**：结尾必须收束完整，绝不能停在半句话或未完成的场景上。
                """.formatted(targetWords));
        prompt.append("\n只输出正文，不要输出标题、序号、解释或字数说明。");
        return llm(user).stream(AiTaskType.WRITE_CHAPTER, contextOf(novel, chapter.getContent()), SYSTEM_PROMPT,
                prompt.toString());
    }

    /**
     * 按大纲规划生成整章正文并落库。
     *
     * **落地逻辑（依赖方向的体现）**：大纲是规划、章节是产物。
     * 若该规划尚未生成过正文，就在此**创建对应章节**（序号=规划序号，标题=规划章节名），
     * 再把正文写入；已生成过则按语义覆盖或追加，并自动留版本快照可回滚。
     */
    public Flux<String> writeChapterAndSave(Long novelId, Long outlineId, User user, Integer totalChapters,
            boolean appendExisting) {
        Novel novel = novelService.loadOwned(novelId, user);
        ChapterOutline outline = loadOutline(novelId, outlineId);
        // 规划 → 章节：没有章节就在此刻落地创建
        Chapter chapter = landChapter(novel, outline, user);
        String title = chapter.getTitle();
        StringBuilder acc = new StringBuilder();
        return writeChapter(novelId, chapter.getId(), user, totalChapters, appendExisting)
                .doOnNext(acc::append)
                .doOnComplete(() -> {
                    String generated = acc.toString().trim();
                    if (generated.isEmpty()) {
                        return;
                    }
                    String content = generated;
                    // 续写模式：把新生成的内容接在原文之后（只增不重）
                    if (appendExisting && chapter.getContent() != null && !chapter.getContent().isBlank()) {
                        content = chapter.getContent().stripTrailing() + "\n\n" + generated;
                    } else {
                        // 重写模式：仅在因输出上限被截断时自动续写补全
                        try {
                            content = completeIfTruncated(novel, user, generated);
                        } catch (Exception e) {
                            // 续写失败不阻断：至少保存已生成的部分
                            log.warn("章节 {} 截断续写失败，保存已生成部分", chapter.getId());
                        }
                    }
                    chapterService.save(chapter.getId(), user, new SaveReq(title, content, "ai"));
                });
    }

    /**
     * 把一条规划落地为章节，并**把规划标题同步到章节**。
     *
     * 标题的权责划分（单向）：
     * - 章节大纲是标题的主场——生成正文时以规划标题为准写入章节；
     * - 章节内容页仍可独立改标题（改的是章节自身），但**不会反向回写大纲**。
     * 因此「在正文页改名 → 再点生成正文」时，标题会以大纲为准；这是有意的单向同步。
     *
     * 这是「章节由大纲生成」的唯一入口。
     */
    private Chapter landChapter(Novel novel, ChapterOutline outline, User user) {
        String plannedTitle = isBlank(outline.getTitle()) ? null : outline.getTitle();
        if (outline.getChapterId() != null) {
            Chapter existing = chapterRepository.findById(outline.getChapterId())
                    .filter(c -> !c.isDeleted())
                    .orElse(null);
            if (existing != null) {
                // 已落地过：仅在规划有标题且与章节不一致时同步标题，不动正文
                if (plannedTitle != null && !plannedTitle.equals(existing.getTitle())) {
                    return chapterRepository.findById(
                            chapterService.save(existing.getId(), user,
                                    new SaveReq(plannedTitle, existing.getContent(), null)).id())
                            .orElseThrow();
                }
                return existing;
            }
        }
        // 规划序号（实体上为非空，这里兜底防拆箱 NPE）
        int order = outline.getChapterOrder() == null ? 1 : outline.getChapterOrder();
        // 同序号的章节（历史数据或用户手工建章）优先复用，避免出现两章同序号
        Chapter byOrder = chapterRepository
                .findFirstByNovelIdAndDeletedFalseAndChapterOrder(novel.getId(), order)
                .orElse(null);
        // 标题优先级：规划标题 > 已有章节标题 > 兜底占位名
        String title = plannedTitle != null ? plannedTitle : (byOrder == null ? null : byOrder.getTitle());
        // ChapterService 返回的是 VO（视图对象），取回 id 后再取实体供后续使用
        Long chapterId = (byOrder != null)
                ? chapterService.save(byOrder.getId(), user, new SaveReq(title, byOrder.getContent(), null)).id()
                : chapterService.createAtOrder(novel.getId(), user, order, title).id();
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow();
        // 规划与产物关联（chapterId 必须落库，否则下次无法复用同一章）
        outline.setChapterId(chapterId);
        chapterOutlineRepository.save(outline);
        return chapter;
    }

    /** 最多自动续写轮数，避免异常情况下无限生成 */
    private static final int MAX_CONTINUE_ROUNDS = 3;
    private static final Duration CONTINUE_TIMEOUT = Duration.ofMinutes(5);

    /**
     * 检测首轮输出是否被 token 上限截断；若是，从断点续写直到情节闭合。
     * 这样即使单章目标字数较大，也不会出现"写到一半句子断掉"的废稿。
     * 对外暴露以供自动创作流水线（自行落库，不走 writeChapterAndSave）复用。
     */
    public String completeIfTruncated(Novel novel, User user, String content) {
        String current = content;
        for (int round = 1; round <= MAX_CONTINUE_ROUNDS; round++) {
            if (!looksTruncated(current)) {
                break;
            }
            StringBuilder more = new StringBuilder();
            llm(user).stream(AiTaskType.WRITE_CHAPTER,
                    contextOf(novel, current),
                    SYSTEM_PROMPT,
                    continueChapterPrompt(novel, current, round))
                    .doOnNext(more::append)
                    .blockLast(CONTINUE_TIMEOUT);
            String extra = more.toString().trim();
            if (extra.isEmpty()) {
                break;
            }
            current = current + extra;
        }
        return current;
    }

    /** 判断内容是否像被硬截断：结尾缺少句末标点，或末段明显过短 */
    static boolean looksTruncated(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String trimmed = content.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        boolean endsWithPunctuation = "。！？…”』」）】…!?.".indexOf(last) >= 0;
        if (!endsWithPunctuation) {
            return true;
        }
        // 末段过短也视为可疑（可能刚好被切在半段）
        String[] paras = trimmed.split("\\n\\s*\\n");
        String lastPara = paras[paras.length - 1].trim();
        return lastPara.length() < 12;
    }

    /**
     * 续写提示词：明确"承接上文继续写，不要重复已写内容"。
     * 注意：Java 文本块中以 + 开头的行会被当作字面量（不是拼接运算符），
     * 因此这里用 formatted() 一次性拼好，绝不把 + 表达式写进文本块内部。
     */
    private String continueChapterPrompt(Novel novel, String existing, int round) {
        String promptTemplate = """

                【任务】请继续把这一章写完。

                【已写到的内容（结尾）】
                %s

                【续写要求】
                1. **直接从上面最后一句之后接着写**，不要重复已写内容、不要另起炉灶、不要重新开头；
                2. 承接上文的时间、地点、人物状态与正在发生的事；
                3. 本章尚未收尾：继续推进情节，并把本章主要矛盾给出阶段性结果，使情节完整闭合；
                4. 结尾必须写完整、写闭合，绝不能停在半句话上；
                5. 本次续写约 %d 字。

                只输出续写的正文，不要解释、不要标题。
                """;
        return baseContext(novel)
                + promptTemplate.formatted(tail(existing, 1500), 1000 + round * 500);
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
    public Flux<String> generateChapterOutline(Long novelId, Long outlineId, User user) {
        return generateChapterOutline(novelId, outlineId, user, null);
    }

    /**
     * 同上，但可显式指定全书总章节数（用于识别最后一章，走完结规划）。
     * 自动创作流水线边建规划行边生成，必须显式传入，否则无法识别终章；
     * 留空时按已有规划行数推断（大纲先行模型下二者一致）。
     */
    public Flux<String> generateChapterOutline(Long novelId, Long outlineId, User user, Integer totalChapters) {
        Novel novel = novelService.loadOwned(novelId, user);
        ChapterOutline outline = loadOutline(novelId, outlineId);
        // 全书总章节数：显式传入优先；否则以规划行数为准（大纲先行，章节由规划落地）
        int total = totalChapters != null && totalChapters > 0
                ? totalChapters
                : chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novel.getId()).size();
        boolean isFinalChapter = isFinalPlannedChapter(outline, total);
        // 该规划若已落地章节，带着已有正文让规划更贴合
        String existingContent = null;
        Long chapterId = outline.getChapterId();
        if (chapterId != null) {
            Chapter landed = chapterRepository.findById(chapterId).orElse(null);
            if (landed != null && !isBlank(landed.getContent())) {
                existingContent = landed.getContent();
            }
        }
        String prompt = buildChapterOutlinePrompt(novel, outline, isFinalChapter, existingContent);
        StringBuilder acc = new StringBuilder();
        return llm(user).stream(AiTaskType.CHAPTER_OUTLINE, contextOf(novel, existingContent),
                SYSTEM_PROMPT, prompt)
                .doOnNext(acc::append)
                .doOnComplete(() -> persistOutlineAndTitle(novel, outline, acc.toString().trim(), user));
    }

    /**
     * 章节大纲 prompt 组装（抽成包级可见便于单测固化关键约束）。
     *
     * 历史 bug 修复：全书大纲（novel.outline，含结局/完结描述）**必须**进 prompt。
     * 此前 prompt 只带「作品信息」，却要求模型“与全书大纲方向一致”——模型根本看不到大纲，
     * 导致一键生成的最后一章大纲没有任何完结规划。
     */
    String buildChapterOutlinePrompt(Novel novel, ChapterOutline outline, boolean isFinalChapter,
            String existingContent) {
        StringBuilder prompt = new StringBuilder(contextBuilder.buildNovelInfo(novel));
        // 全书大纲：主线走向与结局规划的唯一来源；取尾部（结局/完结描述一般在文末）
        if (!isBlank(novel.getOutline())) {
            prompt.append("\n【全书大纲（全书主线走向与结局规划，本章规划必须严格遵循）】\n")
                    .append(tail(novel.getOutline(), BOOK_OUTLINE_TAIL_CHARS)).append("\n");
        }
        prompt.append("\n【当前要规划的章节】第").append(outline.getChapterOrder()).append("章");
        if (isFinalChapter) {
            prompt.append("（全书最后一章）");
        }
        if (!isBlank(outline.getTitle())) {
            prompt.append("（已拟定章节名：").append(outline.getTitle()).append("）");
        }
        prompt.append("\n");
        if (!isBlank(outline.getOutline())) {
            prompt.append("（已有大纲草稿，可据此补全细化）\n【已有草稿】\n")
                    .append(tail(outline.getOutline(), 2000)).append("\n");
        }
        if (!isBlank(existingContent)) {
            prompt.append("【该章已有正文（规划须与已写内容一致）】\n")
                    .append(tail(existingContent, 2000)).append("\n");
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
                4. 本章若不是全书最后一章：在上述结果之后，再为下一章留一个自然的悬念或期待（钩子），不得用钩子代替本章的收束；
                5. 严格遵循上方“全书大纲”：本章的情节推进必须落在主线走向上，不得偏离主线、不得尾大不掉。
                """);
        if (isFinalChapter) {
            prompt.append("""

                    【本章是全书最后一章：完结规划（最高优先级）】
                    本章大纲必须收束全书，并依据全书大纲中的结局/完结部分进行规划：
                    1. 全书核心矛盾与主线悬念在此章给出最终结果或答案；
                    2. 主要人物的处境、关系与成长弧光有明确归宿；
                    3. 呼应前文关键伏笔，让读者感到故事完整地结束，而非戛然而止；
                    4. 严禁再预留引向下一章的钩子或开放式收尾。
                    """);
        }
        prompt.append("""

                【章节名要求】
                - 必须是**具体、贴合本章核心事件**的标题（如「雨夜惊变」「她终于开了口」「F级异能的代价」），
                  要能让读者一眼看出本章发生了什么，禁止使用「未命名章节」「第X章」「待定」这类占位名；
                - 长度 6-16 字，不要加书名号、标点结尾或序号（如「第X章」）；
                - 贴合本书语言风格，不要使用其他章节已用过的标题。

                只输出上述格式内容，不要写正文，不要额外解释。
                """);
        return prompt.toString();
    }

    /** 是否为全书最后一章的规划：规划序号不小于全书总章节数即为终章 */
    static boolean isFinalPlannedChapter(ChapterOutline outline, int totalChapters) {
        return outline != null && outline.getChapterOrder() != null
                && totalChapters > 0 && outline.getChapterOrder() >= totalChapters;
    }

    /**
     * 错别字/用词校对：把文本中的错别字、别字、形近字误用、明显语病与标点问题列出来。
     * 输出 JSON 数组（每条含 wrong/suggestion/type/reason），供前端逐条定位与采纳修正。
     * 长度受实时生成限制，超长文本会截断——长章节建议分段检查。
     */
    public Flux<String> proofread(String text, User user) {
        String src = text == null ? "" : text.trim();
        if (src.isEmpty()) {
            return Flux.just("[]");
        }
        String prompt = """
                请校对下面这段中文网络小说文本，找出其中的错误。

                【检查范围】
                1. 错别字与别字（如同音字、形近字误用）；
                2. 成语/词语误用（如"迫不急待"→"迫不及待"、"一如继往"→"一如既往"）；
                3. 明显的语病（搭配不当、成分残缺、句式杂糅等）；
                4. 标点符号问题（引号不配对、顿号与逗号混用、书名号误用等）；
                5. 同一段内前后称呼不一致（如先称"他/她"后改用姓名，造成指代混乱）。

                【严格排除——这些不是错误，不要报告】
                - 合理的口语、方言、语气词与网文常用表达；
                - 角色名、地名、专有设定（如"异能等级""F级"）等本书自定义名词；
                - 刻意使用的错别字角色、吐槽体、网络梗；
                - 任何你不确定是否为错的表达（宁可漏报，不可误报）。

                【输出格式】
                只输出一个 JSON 数组，每个元素为：
                {"wrong":"原文中的错误片段","suggestion":"建议改为","type":"错别字|词语误用|语病|标点|称呼不一致","reason":"简短说明"}

                要求：
                - wrong 必须是原文中**连续出现、逐字一致**的片段（便于定位替换），不得改写或省略；
                - 若通篇无错，输出空数组 []；
                - 最多输出 50 条，按出现顺序排列；
                - 只输出 JSON，不要 Markdown 代码块标记，不要任何解释文字。

                【待校对文本】
                """ + src;
        return llm(user).stream(AiTaskType.PROOFREAD,
                new AiContext("校对", "", java.util.List.of(), src), SYSTEM_PROMPT, prompt);
    }

    /**
     * 解析 AI 输出（章节名 + 大纲）并分别落库到这条**规划行**上。
     * 解析失败时按纯大纲处理（兼容旧格式），不影响大纲功能。
     */
    private void persistOutlineAndTitle(Novel novel, ChapterOutline outline, String raw, User user) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String title = null;
        String text = raw;
        java.util.regex.Matcher m = TITLE_PATTERN.matcher(raw);
        if (m.find()) {
            title = normalizeTitle(m.group(1));
            text = raw.substring(m.end()).trim();
        }
        text = stripLeadingLabel(text, "大纲");
        boolean changed = false;
        if (!text.isBlank()) {
            outline.setOutline(text);
            changed = true;
        }
        // 章节名写回规划行：仅当用户尚未命名时才用 AI 的名字
        if (title != null && isPlaceholderTitle(outline.getTitle())) {
            outline.setTitle(title);
            changed = true;
        }
        if (changed) {
            chapterOutlineRepository.save(outline);
        }
        // 同步更新已落地章节的标题（若该规划已生成过正文）
        if (title != null && outline.getChapterId() != null) {
            Chapter landed = chapterRepository.findById(outline.getChapterId()).orElse(null);
            if (landed != null && isPlaceholderTitle(landed.getTitle()) && isBlank(landed.getContent())) {
                chapterService.save(landed.getId(), user, new SaveReq(title, null, "ai-title"));
            }
        }
    }

    /** 章节名输出行：`章节名：xxx` */
    private static final java.util.regex.Pattern TITLE_PATTERN = java.util.regex.Pattern
            .compile("^\\s*(?:章节名|标题)\\s*[:：]\\s*(.+?)\\s*$", java.util.regex.Pattern.MULTILINE);

    /** 去除标题中的书名号/序号/尾部标点，并做长度兜底 */
    static String normalizeTitle(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("[《》「」『』\"'“”‘’]", "")
                .replaceAll("^第\\s*[\\d〇零一二三四五六七八九十百千万]+\\s*[章回节卷][：:、.\\s]*", "")
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
        List<ChapterOutline> outlines = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novel.getId());
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
        ChapterOutline outline = findOutlineByOrder(novel.getId(), chapter.getChapterOrder());
        if (outline != null && !isBlank(outline.getOutline())) {
            prompt.append("【本章已有写作大纲（标题须贴合这些要点）】\n")
                    .append(tail(outline.getOutline(), 1500)).append("\n");
        }
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

    /** 上一章结尾注入的字符数：越大越连贯，但会挤压本章可用上下文 */
    private static final int PREV_TAIL_CHARS = 2000;
    /** 章节大纲 prompt 中全书大纲的截取长度：取尾部，保住结局/完结描述 */
    private static final int BOOK_OUTLINE_TAIL_CHARS = 2500;
    /** 前文大纲概览最多回溯几章 */
    private static final int RECENT_OUTLINE_CHAPTERS = 3;
    private static final int RECENT_OUTLINE_CHARS = 260;

    /**
     * 注入前若干章的大纲概览（不含本章与上一章）：维持长线主线、人物状态与未回收伏笔的一致性，
     * 避免"每章像另起炉灶"。按章序倒序收集，再正序输出。
     */
    private void appendRecentOutlinesBrief(StringBuilder prompt, Long novelId, int currentOrder) {
        // 前文概览直接取规划行（章节可能尚未生成，但规划已定）
        List<ChapterOutline> prior = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novelId).stream()
                .filter(o -> o.getChapterOrder() < currentOrder)
                .toList();
        if (prior.isEmpty()) {
            return;
        }
        int fromIndex = Math.max(0, prior.size() - RECENT_OUTLINE_CHAPTERS);
        String header = "\n【前文剧情回顾（务必与之一致，不要另起炉灶）】\n";
        StringBuilder sb = new StringBuilder(header);
        for (int i = fromIndex; i < prior.size(); i++) {
            ChapterOutline o = prior.get(i);
            String brief = o.getOutline();
            if (isBlank(brief) && o.getChapterId() != null) {
                // 无大纲时退化为该章正文尾部，保证至少有"前情"
                Chapter landed = chapterRepository.findById(o.getChapterId()).orElse(null);
                brief = landed == null ? null : tail(landed.getContent(), 160);
            }
            if (isBlank(brief)) {
                continue;
            }
            sb.append("第").append(o.getChapterOrder()).append("章：")
                    .append(tail(brief, RECENT_OUTLINE_CHARS)).append("\n");
        }
        if (sb.length() > header.length()) {
            prompt.append(sb);
        }
    }

    /**
     * 每章目标字数：按“总目标字数 / 章节数”动态均摊，并**封顶到单次输出能力之内**。
     *
     * 关键：LLM 单次生成有 token 上限（max-output-tokens）。若均摊后的每章字数远超该上限，
     * 模型写到一半就会被硬截断（句子断在半截，前言不搭后语）。因此这里做上限保护，
     * 并提示用户"总字数/章节数"设置需要调整——总字数除以章节数才是真正的单章篇幅。
     */
    private static final int FALLBACK_CHAPTER_WORDS = 1500;
    /** 单章目标字数上限：与 max-output-tokens(16384) 对齐，留出余量 */
    private static final int MAX_CHAPTER_WORDS = 6000;

    /**
     * 每章目标字数。优先级：
     * 1. 用户对本章的单独设定（ChapterOutline.targetWords）；
     * 2. 全书均摊：ceil(总目标字数 / 章节数)；
     * 3. 兜底 FALLBACK_CHAPTER_WORDS。
     * 并封顶到单次输出能力之内——LLM 单次生成有 token 上限，超出会被硬截断（句子断在半截）。
     */
    private int perChapterTargetWords(Novel novel, Long chapterId) {
        if (chapterId != null) {
            ChapterOutline byChapter = chapterOutlineRepository.findByChapterId(chapterId).orElse(null);
            if (byChapter != null && byChapter.getTargetWords() != null && byChapter.getTargetWords() > 0) {
                return Math.min(byChapter.getTargetWords(), MAX_CHAPTER_WORDS);
            }
        }
        Integer total = novel.getTargetWords();
        // 均摊基数用「规划条数」：大纲先行于章节，规划数才是全书真实章节数
        long count = chapterOutlineRepository.findByNovelIdOrderByChapterOrderAsc(novel.getId()).size();
        if (count == 0) {
            count = chapterRepository.countByNovelIdAndDeletedFalse(novel.getId());
        }
        int words = FALLBACK_CHAPTER_WORDS;
        if (total != null && total > 0 && count > 0) {
            words = (int) Math.ceil((double) total / count);
        }
        return Math.min(words, MAX_CHAPTER_WORDS);
    }

    private Chapter loadChapter(Long novelId, Long chapterId) {
        return chapterRepository.findById(chapterId)
                .filter(c -> c.getNovelId().equals(novelId) && !c.isDeleted())
                .orElseThrow(() -> new BizException(404, "章节不存在", 404));
    }

    /** 加载一条大纲规划行（含属主校验，越权返回 404） */
    private ChapterOutline loadOutline(Long novelId, Long outlineId) {
        return chapterOutlineRepository.findById(outlineId)
                .filter(o -> o.getNovelId().equals(novelId))
                .orElseThrow(() -> new BizException(404, "大纲不存在", 404));
    }

    /** 按规划序号取该书的大纲行（正文生成时定位"本章规划"） */
    private ChapterOutline findOutlineByOrder(Long novelId, int chapterOrder) {
        return chapterOutlineRepository.findByNovelIdAndChapterOrder(novelId, chapterOrder).orElse(null);
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
