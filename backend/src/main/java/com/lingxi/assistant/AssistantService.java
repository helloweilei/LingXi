package com.lingxi.assistant;

import com.lingxi.ai.AiContext;
import com.lingxi.ai.AiTaskType;
import com.lingxi.ai.LlmClientProvider;
import com.lingxi.common.BizException;
import com.lingxi.user.User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 创作助手：DeepSeek 式自由聊天，边聊边收集创作诉求；
 * 诉求齐备（用户表示没有更多要求）后一键生成小说大纲。
 *
 * 设计为**无状态**：对话历史由前端每次全量携带，不落库；
 * 页面刷新由前端 localStorage 恢复。这样不引入新表，也天然支持多端同时使用。
 */
@Service
public class AssistantService {

    /** 携带的对话条数上限：防止超长请求撑爆上下文，超出时保留最近的部分 */
    static final int MAX_MESSAGES = 40;
    /** 单条消息保留的字符数上限 */
    static final int MAX_MSG_CHARS = 2000;

    private static final String SYSTEM_PROMPT = """
            你是「灵犀创作」平台的创作助手，亲切专业、深谙网文创作。你正在与一位想写小说的作者自由聊天，
            目标是聊着天把 TA 的创作诉求收集齐，最终帮 TA 产出一份可以直接动笔的小说大纲。
            请严格遵守：
            1. 使用简体中文，语气轻松自然，像一位懂行的老朋友，不堆砌客套话；
            2. 每轮回复简短（一般不超过 150 字），先回应对方刚说的内容，再自然引出下一个问题；
            3. 一次最多问 1-2 个问题，优先补齐：题材类型、核心创意或冲突、主角设定、故事基调、大致篇幅；
            4. 用户已经说清楚的内容不要反复追问；用户跑题时礼貌地把话题拉回创作；
            5. 输出纯文本，不要使用 Markdown 语法；
            6. 只有当核心诉求已基本齐全、且用户明确表示没有更多要求时（如“没有了”“就这些”“可以了”），
               先用两三句给出诉求小结，然后在回复最末尾单独一行输出：【诉求已收集完毕】
               诉求未齐或用户可能还有话说时，严禁输出该标记。
            """;

    private final LlmClientProvider llmClientProvider;

    public AssistantService(LlmClientProvider llmClientProvider) {
        this.llmClientProvider = llmClientProvider;
    }

    /* ---------- 开场引导问题：AI 依据当前热门题材生成，失败时兜底静态模板 ---------- */

    /** 开场问题条数 */
    static final int OPENERS_COUNT = 3;
    /** 单条开场问题的长度上限：明显超长视为模型跑题，丢弃 */
    static final int MAX_OPENER_CHARS = 40;
    /** 开场问题缓存有效期：热门题材变化不快，避免每次进入页面都调一次模型 */
    static final long OPENERS_TTL_MS = 10 * 60 * 1000L;
    /** 模型不可用 / 输出不可解析时的静态兜底模板 */
    static final List<String> FALLBACK_OPENERS = List.of(
            "我想写玄幻修仙，帮我出出主意",
            "我有个灵感画面，想跟你聊聊怎么展开",
            "还没想好，先帮我头脑风暴几个脑洞");

    private static final String OPENERS_SYSTEM_PROMPT = """
            你是「灵犀创作」平台的创作助手，深谙中文网文市场，熟悉当下热门题材与流行脑洞。
            你的任务是为初次使用创作助手的作者设计开场引导问题，帮 TA 顺利迈出第一步。
            """;

    private static final String OPENERS_TASK = """
            【任务】
            请结合当前中文网文的热门题材与流行脑洞（如玄幻、都市异能、科幻末日、规则怪谈、无限流、种田日常等），
            设计 %d 条开场引导问题。

            要求：
            1. 每条一句话、20 字左右，口语化、有画面感，像作者可以原样点选发给你的开场白；
            2. %d 条覆盖不同的热门题材或不同的创作状态（有想法 / 没灵感 / 想聊设定），不要雷同；
            3. 只输出 %d 行纯文本，一行一条，不要序号、不要引号、不要任何解释。
            """.formatted(OPENERS_COUNT, OPENERS_COUNT, OPENERS_COUNT);

    /** 开场问题缓存（全局）：内容与具体用户无关，TTL 内直接复用 */
    private volatile List<String> openersCache = FALLBACK_OPENERS;
    private volatile long openersCachedAt = 0L;

    /**
     * 热门题材开场引导问题：调用模型生成，成功后替换静态模板并缓存；
     * 模型不可用、超时或输出不可解析时返回静态兜底模板（永不抛错）。
     */
    public List<String> hotOpeners(User user) {
        long now = System.currentTimeMillis();
        List<String> cached = openersCache;
        if (cached != null && now - openersCachedAt < OPENERS_TTL_MS) {
            return cached;
        }
        try {
            String answer = llmClientProvider.forUser(user)
                    .stream(AiTaskType.CHAT, assistantCtx(), OPENERS_SYSTEM_PROMPT, OPENERS_TASK)
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(Duration.ofSeconds(12));
            List<String> parsed = parseOpeners(answer);
            if (!parsed.isEmpty()) {
                openersCache = parsed;
                openersCachedAt = now;
                return parsed;
            }
        } catch (Exception e) {
            // 模型不可用 / 超时：静默降级到静态模板，开场引导不因 AI 故障而缺失
        }
        return FALLBACK_OPENERS;
    }

    /**
     * 解析模型输出的开场问题：逐行去掉序号 / 项目符号 / 引号，取前 3 条非空且不过长的。
     * 包级可见便于单测固化清洗规则。
     */
    static List<String> parseOpeners(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.strip()
                    .replaceAll("^[0-9０-９一二三四五六七八九十]+\\s*[.、)）:：]\\s*", "")
                    .replaceAll("^[•·\\-–—*]+\\s*", "")
                    .replaceAll("^[「『\"“]+", "")
                    .replaceAll("[」』\"”]+$", "")
                    .replaceAll("[，,、；;：.。]+$", "")
                    .strip();
            if (line.isEmpty() || line.length() > MAX_OPENER_CHARS) {
                continue;
            }
            out.add(line);
            if (out.size() >= OPENERS_COUNT) {
                break;
            }
        }
        return out;
    }

    /** 创作诉求收集聊天：无状态，每轮携带全量对话，流式返回助手回复 */
    public Flux<String> chat(User user, List<ChatMsg> messages) {
        String prompt = transcript(messages) + """

                【任务】
                请以创作助手的身份回复用户的最新一条消息，按系统要求继续收集创作诉求。
                """;
        return llmClientProvider.forUser(user).stream(AiTaskType.CHAT, assistantCtx(), SYSTEM_PROMPT, prompt);
    }

    /** 根据对话中收集到的诉求，生成一份完整的小说大纲（全书大纲），流式返回 */
    public Flux<String> outline(User user, List<ChatMsg> messages) {
        String prompt = transcript(messages) + """

                【任务】
                请根据以上对话中收集到的创作诉求，生成一份完整的小说大纲：
                1. 书名（1 个主书名，可附最多 2 个备选）；
                2. 题材与基调；
                3. 核心设定（世界观 / 金手指 / 核心悬念）；
                4. 主要人物（3-5 人，含主角，每位一句话概括）；
                5. 主线剧情：按“第一卷 / 第二卷……”或“第一阶段……”推进，
                   每卷先给一句核心冲突，再列 3-6 个关键节点；
                6. 结局走向；
                7. 主题与卖点（一段话）。
                对话中用户表达的想法是最高依据，不得擅自替换；没聊到的细节由你补全并保持自洽。
                结构清晰、可直接落地开写。
                """;
        return llmClientProvider.forUser(user).stream(AiTaskType.OUTLINE, assistantCtx(), SYSTEM_PROMPT, prompt);
    }

    private static AiContext assistantCtx() {
        // 无作品上下文：与创作引导相同的合成上下文方式（任务类型决定用户级模型配置的复用）
        return new AiContext("创作助手", null, new ArrayList<>(), null);
    }

    /**
     * 清洗对话：剔除空白消息、截断超长内容、限制条数（保留最近的）。
     * 包级可见便于单测固化边界行为。
     */
    static List<ChatMsg> sanitize(List<ChatMsg> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BizException(400, "对话内容不能为空", 400);
        }
        List<ChatMsg> kept = new ArrayList<>();
        for (ChatMsg m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(m.role()) ? "assistant" : "user";
            kept.add(new ChatMsg(role, tail(m.content(), MAX_MSG_CHARS)));
        }
        if (kept.isEmpty()) {
            throw new BizException(400, "对话内容不能为空", 400);
        }
        return kept.size() <= MAX_MESSAGES ? kept : kept.subList(kept.size() - MAX_MESSAGES, kept.size());
    }

    /** 把对话渲染成 prompt 里的时间线文本，包级可见便于单测 */
    static String transcript(List<ChatMsg> messages) {
        StringBuilder sb = new StringBuilder("【对话记录（按时间先后）】\n");
        for (ChatMsg m : sanitize(messages)) {
            sb.append("assistant".equals(m.role()) ? "助手：" : "用户：")
                    .append(m.content().strip())
                    .append("\n");
        }
        return sb.toString();
    }

    private static String tail(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() <= max ? t : t.substring(t.length() - max);
    }

    /** 一条对话消息：role 取 user / assistant */
    public record ChatMsg(String role, String content) {
    }
}
