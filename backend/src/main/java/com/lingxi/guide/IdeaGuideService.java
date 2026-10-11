package com.lingxi.guide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.ai.AiContext;
import com.lingxi.ai.AiTaskType;
import com.lingxi.ai.LlmClientProvider;
import com.lingxi.character.CharacterService;
import com.lingxi.character.dto.CharacterDtos;
import com.lingxi.common.BizException;
import com.lingxi.guide.dto.GuideDtos;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelRepository;
import com.lingxi.novel.NovelService;
import com.lingxi.novel.dto.NovelDtos;
import com.lingxi.user.User;
import com.lingxi.world.WorldSettingService;
import com.lingxi.world.dto.WorldDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 启发式创作引导：把「零想法 / 一句话种子」通过问答或扩展，转化为多套原创小说方案，
 * 并支持选用方案一键生成带大纲 / 角色 / 世界观的作品。
 */
@Service
public class IdeaGuideService {

    private static final String SYSTEM_PROMPT = """
            你是「灵犀创作」平台内置的资深中文网文策划与创作助手。你的任务是根据用户提供的创作种子
            （题材 / 基调 / 读者 / 冲突 / 篇幅 / 平台，或一段模糊想法），策划出多个可落地的原创小说方案。
            请严格遵守：
            1. 使用简体中文；方案必须原创、具体、相互之间有差异化；
            2. 只输出符合要求的 JSON 数组，不要输出任何解释、前言、结语或 Markdown 代码块标记；
            3. 每个方案都要给出可操作的世界观与主要人物，便于后续直接进入写作。
            """;

    private static final List<GuideDtos.Question> SCRIPT = List.of(
            new GuideDtos.Question("genre", "题材", "你更想写哪类题材？（也可自行填写）",
                    List.of("玄幻", "都市", "悬疑", "言情", "科幻", "历史", "奇幻", "游戏竞技", "轻小说")),
            new GuideDtos.Question("tone", "基调", "希望整体是什么基调？（也可自行填写）",
                    List.of("热血", "轻松", "暗黑", "治愈", "烧脑", "搞笑", "甜宠", "群像")),
            new GuideDtos.Question("audience", "目标读者", "主要写给谁看？（也可自行填写）",
                    List.of("男频热血粉", "女频甜宠党", "泛科幻读者", "全年龄", "悬疑推理迷", "二次元")),
            new GuideDtos.Question("conflict", "核心冲突", "最能驱动故事的核心冲突是什么？（也可自行填写）",
                    List.of("复仇", "成长", "解密", "救赎", "逆袭", "守护", "权谋", "生存")),
            new GuideDtos.Question("length", "篇幅期望", "预计篇幅多大？（也可自行填写）",
                    List.of("短篇10万字内", "中篇30-50万", "长篇百万+", "连载不限")),
            new GuideDtos.Question("words", "目标字数", "全书大概想写多少字？可选下方档位，也可直接填确切数字（如「50万」「100万字」「8000字」）",
                    List.of("10万", "30万", "50万", "100万", "200万", "不设上限")),
            new GuideDtos.Question("platform", "目标平台", "打算发到哪些平台？（这也影响节奏与爽点设计，也可自行填写）",
                    List.of("起点", "番茄", "晋江", "自有书城", "多平台同步", "微信读书")));

    private static final Map<String, String> FIELD_LABEL = Map.of(
            "genre", "题材", "tone", "基调", "audience", "目标读者",
            "conflict", "核心冲突", "length", "篇幅期望", "words", "目标字数",
            "platform", "目标平台");

    /** 未指定字数时的默认目标（30 万字） */
    private static final int DEFAULT_TARGET_WORDS = 300000;

    /** 中文数字（用于解析「二十万」这类写法），含十与百 */
    private static final String CN_DIGITS = "零一二三四五六七八九十百";

    private final CreativeIntentRepository repository;
    private final LlmClientProvider llmClientProvider;
    private final NovelService novelService;
    private final NovelRepository novelRepository;
    private final CharacterService characterService;
    private final WorldSettingService worldSettingService;
    private final ObjectMapper objectMapper;

    public IdeaGuideService(CreativeIntentRepository repository, LlmClientProvider llmClientProvider,
                            NovelService novelService, NovelRepository novelRepository,
                            CharacterService characterService,
                            WorldSettingService worldSettingService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.llmClientProvider = llmClientProvider;
        this.novelService = novelService;
        this.novelRepository = novelRepository;
        this.characterService = characterService;
        this.worldSettingService = worldSettingService;
        this.objectMapper = objectMapper;
    }

    public GuideDtos.GuideStartVO start(User user, String seed) {
        CreativeIntent intent = new CreativeIntent();
        intent.setUserId(user.getId());
        intent.setStatus("COLLECTING");
        boolean hasSeed = seed != null && !seed.isBlank();
        if (hasSeed) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("seed", seed.trim());
            intent.setSeedJson(writeJson(m));
        } else {
            intent.setSeedJson("{}");
        }
        repository.save(intent);
        GuideDtos.Question first = hasSeed ? null : SCRIPT.get(0);
        return new GuideDtos.GuideStartVO(intent.getId(), hasSeed ? "seed" : "guided", first);
    }

    public GuideDtos.AnswerVO answer(User user, Long id, String answer) {
        CreativeIntent intent = loadOwned(id, user);
        Map<String, String> answers = readAnswers(intent.getSeedJson());
        if (answers.containsKey("seed")) {
            return new GuideDtos.AnswerVO(null, true, SCRIPT.size(), SCRIPT.size());
        }
        int step = answers.size();
        if (step >= SCRIPT.size()) {
            return new GuideDtos.AnswerVO(null, true, step, SCRIPT.size());
        }
        GuideDtos.Question q = SCRIPT.get(step);
        answers.put(q.field(), answer == null ? "" : answer.trim());
        intent.setSeedJson(writeJson(answers));
        repository.save(intent);
        int next = answers.size();
        if (next >= SCRIPT.size()) {
            return new GuideDtos.AnswerVO(null, true, next, SCRIPT.size());
        }
        return new GuideDtos.AnswerVO(SCRIPT.get(next), false, next, SCRIPT.size());
    }

    /**
     * 历史引导会话列表（首页「历史方案」入口）。
     * 返回摘要而非完整提案详情——完整详情由 GET /api/guide/{id} 按需获取。
     */
    public List<GuideDtos.IntentSummaryVO> history(User user, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        List<CreativeIntent> intents = repository.findByUserIdOrderByIdDesc(user.getId(), PageRequest.of(0, size));

        // 批量加载关联作品，避免逐条查询（N+1）
        List<Long> novelIds = intents.stream()
                .map(CreativeIntent::getNovelId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Novel> novelById = novelIds.isEmpty()
                ? Map.of()
                : novelRepository.findAllById(novelIds).stream()
                        .collect(Collectors.toMap(Novel::getId, n -> n, (a, b) -> a));

        List<GuideDtos.IntentSummaryVO> result = new ArrayList<>();
        for (CreativeIntent intent : intents) {
            Map<String, String> answers = readAnswers(intent.getSeedJson());
            List<GuideDtos.ProposalVO> proposals = parseProposals(intent.getProposalJson());
            List<String> titles = proposals.stream()
                    .map(p -> p.title() == null || p.title().isBlank() ? "未命名方案" : p.title())
                    .toList();
            Novel novel = intent.getNovelId() == null ? null : novelById.get(intent.getNovelId());
            result.add(new GuideDtos.IntentSummaryVO(
                    intent.getId(), intent.getStatus(), answers.get("seed"),
                    answers, titles, proposals.size(), intent.getSelectedId(),
                    intent.getNovelId(), novel == null ? null : novel.getTitle(), novel != null,
                    intent.getCreatedAt(), intent.getUpdatedAt()));
        }
        return result;
    }

    public GuideDtos.GuideStateVO getState(User user, Long id) {
        CreativeIntent intent = loadOwned(id, user);
        Map<String, String> answers = readAnswers(intent.getSeedJson());
        List<GuideDtos.ProposalVO> proposals = parseProposals(intent.getProposalJson());
        // 种子模式无问答进度；引导模式按已答数定位下一题（供"继续未完成的问答"恢复现场）
        boolean guided = !answers.containsKey("seed");
        int total = SCRIPT.size();
        int step = guided ? Math.min(answers.size(), total) : total;
        GuideDtos.Question next = guided && step < total ? SCRIPT.get(step) : null;
        return new GuideDtos.GuideStateVO(intent.getStatus(), answers.get("seed"), answers,
                proposals, intent.getSelectedId(), step, total, next);
    }

    /**
     * 生成创意提案（流式）。流式文本在 onComplete 时解析为 JSON 数组并落到 creative_intents.proposal_json。
     */
    public reactor.core.publisher.Flux<String> generate(User user, Long id) {
        CreativeIntent intent = loadOwned(id, user);
        Map<String, String> answers = readAnswers(intent.getSeedJson());
        String prompt;
        String ctxSource;
        String genre = "";
        if (answers.containsKey("seed")) {
            prompt = buildSeedPrompt(answers.get("seed"));
            ctxSource = "种子：" + answers.get("seed");
        } else {
            prompt = buildGuidedPrompt(answers);
            genre = answers.getOrDefault("genre", "");
            ctxSource = "引导答案：" + answers;
        }
        com.lingxi.ai.LlmClient client = llmClientProvider.forUser(user);
        AiContext ctx = new AiContext("灵犀创作引导", genre, new ArrayList<>(), ctxSource);
        StringBuilder acc = new StringBuilder();
        return client.stream(AiTaskType.GUIDE_PROPOSAL, ctx, SYSTEM_PROMPT, prompt)
                .doOnNext(acc::append)
                .doOnComplete(() -> saveProposals(intent, acc.toString().trim()));
    }

    @Transactional
    public GuideDtos.SelectResult select(User user, Long id, int index) {
        CreativeIntent intent = loadOwned(id, user);
        List<JsonNode> proposals = parseProposalNodes(intent.getProposalJson());
        if (index < 0 || index >= proposals.size()) {
            throw new BizException(400, "无效的方案序号", 400);
        }
        JsonNode p = proposals.get(index);
        String title = str(p, "title", "未命名新书");
        String premise = str(p, "premise", "");
        Map<String, String> answers = readAnswers(intent.getSeedJson());
        // 目标字数：优先取用户填写的「确切字数」，没有则按篇幅期望档位推导
        Integer exactWords = parseExactWords(answers.get("words"));
        int targetWords = exactWords != null
                ? exactWords
                : deriveTarget(answers.getOrDefault("length", ""));
        NovelDtos.CreateReq createReq = new NovelDtos.CreateReq(
                title, limit(premise, 1000), limit(answers.getOrDefault("genre", ""), 30), targetWords, null, null);
        NovelDtos.VO novel = novelService.create(user, createReq);
        Long novelId = novel.id();

        String outline = str(p, "outline", "");
        if (!outline.isBlank()) {
            novelService.update(novelId, user,
                    NovelDtos.UpdateReq.builder().outline(limit(outline, 30000)).build());
        }

        JsonNode chars = p.get("characters");
        if (chars != null && chars.isArray()) {
            for (JsonNode c : chars) {
                characterService.create(novelId, user, new CharacterDtos.SaveReq(
                        str(c, "name", "角色"), str(c, "role", "配角"), str(c, "description", null)));
            }
        }
        JsonNode worlds = p.get("worlds");
        if (worlds != null && worlds.isArray()) {
            for (JsonNode w : worlds) {
                worldSettingService.create(novelId, user, new WorldDtos.SaveReq(
                        str(w, "title", "世界观"), str(w, "content", null)));
            }
        }

        intent.setStatus("READY");
        intent.setSelectedId(index);
        intent.setNovelId(novelId);
        repository.save(intent);
        return new GuideDtos.SelectResult(novelId);
    }

    @Transactional
    public void reset(User user, Long id) {
        CreativeIntent intent = loadOwned(id, user);
        intent.setSeedJson("{}");
        intent.setProposalJson(null);
        intent.setSelectedId(null);
        intent.setNovelId(null);
        intent.setStatus("COLLECTING");
        repository.save(intent);
    }

    /**
     * 删除历史引导会话（仅删除意图本身：问答答案与创意方案）。
     * 已由该会话生成的作品是独立实体，不受影响。
     */
    @Transactional
    public void delete(User user, Long id) {
        CreativeIntent intent = loadOwned(id, user);
        repository.delete(intent);
    }

    private CreativeIntent loadOwned(Long id, User user) {
        return repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BizException(404, "引导会话不存在", 404));
    }

    private Map<String, String> readAnswers(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return m;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                node.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText("")));
            }
        } catch (Exception ignored) {
            // 解析失败时返回空，引导可重新开始
        }
        return m;
    }

    private String writeJson(Map<String, String> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<GuideDtos.ProposalVO> parseProposals(String json) {
        return parseProposalNodes(json).stream().map(this::toProposalVO).toList();
    }

    private List<JsonNode> parseProposalNodes(String json) {
        List<JsonNode> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        String cleaned = extractJsonArray(json);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.isArray()) {
                node.forEach(list::add);
            }
        } catch (Exception ignored) {
            // 解析失败：保留空，前端可重新生成
        }
        return list;
    }

    private String extractJsonArray(String s) {
        int a = s.indexOf('[');
        int b = s.lastIndexOf(']');
        if (a >= 0 && b > a) {
            return s.substring(a, b + 1);
        }
        return s;
    }

    private GuideDtos.ProposalVO toProposalVO(JsonNode p) {
        List<GuideDtos.PropCharacter> chars = new ArrayList<>();
        JsonNode cNode = p.get("characters");
        if (cNode != null && cNode.isArray()) {
            cNode.forEach(c -> chars.add(new GuideDtos.PropCharacter(
                    str(c, "name", ""), str(c, "role", ""), str(c, "description", ""))));
        }
        List<GuideDtos.PropWorld> worlds = new ArrayList<>();
        JsonNode wNode = p.get("worlds");
        if (wNode != null && wNode.isArray()) {
            wNode.forEach(w -> worlds.add(new GuideDtos.PropWorld(str(w, "title", ""), str(w, "content", ""))));
        }
        return new GuideDtos.ProposalVO(
                str(p, "title", ""), str(p, "premise", ""), str(p, "world", ""),
                str(p, "protagonist", ""), str(p, "outline", ""), chars, worlds, str(p, "platform", ""));
    }

    private static String str(JsonNode n, String k, String def) {
        JsonNode v = n.get(k);
        return v == null ? def : v.asText(def);
    }

    private static String limit(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 由「篇幅期望」推导全书目标字数。
     * 优先识别用户填写的**确切字数**（如「50万」「100万字」「8000字」「120w」），取不到再按短/中/长回落。
     * 这样问答模式下用户既能选粗档，也能直接写具体字数。
     */
    static int deriveTarget(String length) {
        Integer exact = parseExactWords(length);
        if (exact != null) {
            return exact;
        }
        if (length == null) {
            return DEFAULT_TARGET_WORDS;
        }
        if (length.contains("短")) {
            return 100000;
        }
        if (length.contains("长")) {
            return 1000000;
        }
        if (length.contains("中")) {
            return 300000;
        }
        return DEFAULT_TARGET_WORDS;
    }

    /** 从自然语言中解析确切字数，支持「50万」「100万字」「8000字」「120w」「二十万」等写法；无法解析返回 null */
    private static Integer parseExactWords(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        // 「二十万」等中文数字：必须紧邻「万/字/w」等量词才采信，避免「中篇」「十二生肖」等误判
        java.util.regex.Matcher cn = Pattern
                .compile("([零一二三四五六七八九十百]+)\\s*(万|字|w|W)").matcher(s);
        if (cn.find()) {
            int val = parseChineseNumber(cn.group(1));
            if (val > 0) {
                int total = applyUnit(val, cn.group(2));
                if (total > 0) {
                    return normalizeWords(total);
                }
            }
        }
        // 阿拉伯数字：「50万」「100万字」「8000字」「120w」「中篇30-50万」→ 取其中最大者（区间取上限）
        java.util.regex.Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([万wW千kK]?)").matcher(s);
        int best = 0;
        while (m.find()) {
            try {
                double num = Double.parseDouble(m.group(1));
                best = Math.max(best, applyUnit((int) Math.round(num), m.group(2)));
            } catch (NumberFormatException ignored) {
                // 继续尝试下一个匹配
            }
        }
        return best > 0 ? normalizeWords(best) : null;
    }

    private static int applyUnit(int num, String unit) {
        if (unit == null || unit.isBlank()) {
            return num;
        }
        char c = unit.charAt(0);
        if (c == '万' || c == 'w' || c == 'W') {
            return (int) (num * 10_000);
        }
        if (c == '千' || c == 'k' || c == 'K') {
            return num * 1000;
        }
        return num;
    }

    /** 约束到合理区间（1 万 ~ 1000 万字），避免用户误填导致异常 */
    private static int normalizeWords(int words) {
        return Math.max(10_000, Math.min(10_000_000, words));
    }

    private static int parseChineseNumber(String cn) {
        if (cn.isEmpty()) {
            return 0;
        }
        // 常见写法：十 / 二十 / 三十五 / 一百二十（百量级不在此支持，够用即可）
        int total = 0;
        int section = 0;
        int current = 0;
        for (int i = 0; i < cn.length(); i++) {
            int d = CN_DIGITS.indexOf(cn.charAt(i));
            if (d < 0) {
                continue;
            }
            if (d >= 10) {
                // 十(10) / 百(100)
                section += (current == 0 ? 1 : current) * d;
                current = 0;
            } else {
                current = d;
            }
            total = section + current;
        }
        return total;
    }

    private void saveProposals(CreativeIntent intent, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        intent.setProposalJson(raw);
        intent.setStatus("PROPOSING");
        repository.save(intent);
    }

    private String buildGuidedPrompt(Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户填写的创作偏好，请基于它们策划 3 套差异化、可落地的原创小说方案：\n");
        answers.forEach((k, v) -> sb.append("- ").append(FIELD_LABEL.getOrDefault(k, k)).append("：").append(v).append("\n"));
        sb.append("""
                【要求】
                1. 输出一个 JSON 数组，包含 3 个对象；
                2. 每个对象字段：title（书名）、premise（一句话梗概/卖点）、world（世界观要点）、protagonist（主角设定）、outline（分卷大纲草案，用 \\n 换行）、characters（数组，每项 {name,role,description}）、worlds（数组，每项 {title,content}）、platform（目标平台，来自用户输入）；
                3. 三套方案在题材切入点、人设、爽点上要有明显差异；
                4. 只输出 JSON，不要任何额外文字或代码块标记。
                """);
        return sb.toString();
    }

    private String buildSeedPrompt(String seed) {
        return """
                用户目前只有一个模糊的想法：「%1$s」。

                请基于这个模糊想法，扩展并策划 3 套差异化、可落地的原创小说方案：
                1. 输出一个 JSON 数组，包含 3 个对象；
                2. 每个对象字段：title（书名）、premise（一句话梗概/卖点）、world（世界观要点）、protagonist（主角设定）、outline（分卷大纲草案，用 \\n 换行）、characters（数组，每项 {name,role,description}）、worlds（数组，每项 {title,content}）、platform（推断合适的发布平台）；
                3. 三套方案可以围绕同一个核心种子做不同方向的发散（如不同题材、不同基调、不同受众）；
                4. 只输出 JSON，不要任何额外文字或代码块标记。
                """.formatted(seed.replace("%", "%%"));
    }
}
