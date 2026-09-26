package com.lingxi.ai;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置离线示例引擎：无需 API Key 即可完整体验全流程。
 * 生成的是模板化示例文案，接入真实模型请配置 lingxi.ai.provider=openai。
 */
public class MockLlmClient implements LlmClient {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Flux<String> stream(AiTaskType type, AiContext ctx, String systemPrompt, String userPrompt) {
        String full = switch (type) {
            case OUTLINE -> outline(ctx);
            case CONTINUE -> continueText(ctx);
            case WRITE_CHAPTER -> writeChapter(ctx);
            case POLISH -> polish(ctx);
            case EXPAND -> expand(ctx);
            case CHAT -> chat(ctx);
            case SUMMARIZE -> summarize(ctx);
        };
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < full.length()) {
            int end = Math.min(full.length(), i + 5 + (i % 7));
            chunks.add(full.substring(i, end));
            i = end;
        }
        return Flux.fromIterable(chunks).delayElements(Duration.ofMillis(40));
    }

    private String outline(AiContext ctx) {
        String title = blank(ctx.novelTitle()) ? "未命名新书" : ctx.novelTitle();
        String genre = blank(ctx.genre()) ? "都市幻想" : ctx.genre();
        String lead = (ctx.characterNames() == null || ctx.characterNames().isEmpty())
                ? "主角" : ctx.characterNames().get(0);
        return String.format("""
                《%1$s》全书大纲（%2$s）

                【第一卷 · 蛰伏与觉醒】
                核心冲突：平凡生活中意外觉醒的%3$s，被迫卷入一层不为人知的暗流。
                第1章 楔子：一场离奇事件打破日常，%3$s身上初现异象。
                第2章 初试身手：第一次尝试掌控力量，效果出人意料。
                第3章 树欲静而风不止：神秘势力登场，主动找上门来。
                第4章 抉择时刻：留下还是离开？%3$s做出全书第一个关键决定。
                第5章 卷末高潮：正面冲突爆发，%3$s险胜，也付出了代价。

                【第二卷 · 风起云涌】
                核心冲突：踏入更广阔的世界，%3$s在各方势力的博弈中寻找立足之地。
                第6章 新的战场：进入新环境，结识伙伴，也遇上真正的对手。
                第7章 规则之下：摸清这个世界的运行规则与潜规则。
                第8章 暗线浮出：此前埋下的伏笔开始收束，幕后轮廓初现。
                第9章 短兵相接：与主要对手第一次正面交锋，互有胜负。
                第10章 卷末高潮：一场精心策划的反杀，%3$s声名鹊起。

                【第三卷 · 巅峰对决】
                核心冲突：真相浮出水面，昔日盟友与敌人的界限变得模糊。
                第11-14章：真相、背叛、牺牲与成长交织推进。
                第15章 卷末高潮：正邪大战一触即发，%3$s站上风暴中心。

                【第四卷 · 尘埃落定】
                核心冲突：最终决战，与内心的执念和解。
                第16-19章：决战布局，各条支线逐一收束。
                第20章 大结局：胜负揭晓，余韵悠长，全书闭环。

                【主题与卖点】
                以%3$s的成长弧光为主线，兼顾爽感与悬念；%2$s题材的独有质感贯穿始终，每卷末设置高潮反转，保持追读黏性。
                """, title, genre, lead);
    }

    private String writeChapter(AiContext ctx) {
        String hero = (ctx.characterNames() == null || ctx.characterNames().isEmpty())
                ? "他" : ctx.characterNames().get(0);
        String title = blank(ctx.novelTitle()) ? "未命名新书" : ctx.novelTitle();
        String genre = blank(ctx.genre()) ? "都市幻想" : ctx.genre();
        return String.format("""
                清晨的第一缕光穿过窗帘的缝隙，落在书桌边缘那盏还亮着的台灯上。%1$s揉了揉发酸的眉心，一夜未眠的疲惫仿佛在这一刻被光线照得无所遁形。

                窗外的城市刚刚苏醒，远处传来早班车的汽笛声。%1$s的目光落回桌上摊开的笔记，那些字迹在灯下泛着微微的光，像是一道道还未解开的谜题。

                “如果一切从一开始就是局呢？”

                这个念头已经在%1$s脑中盘旋了一整夜。每一次试图抓住它，它便像水中的倒影一样散开，只留下更深一层的困惑。%1$s深吸一口气，将笔记合上，起身走向门口。

                门打开的瞬间，走廊尽头传来一阵急促的脚步声。来人显然也没想到会与%1$s迎面相遇，脚步猛地一顿，脸上的表情在短短一秒内经历了惊讶、戒备，再到故作镇定。

                “真巧。”对方先开了口，声音里带着一丝不易察觉的僵硬。

                %1$s没有立刻回答，只是静静地看着对方，目光平静得近乎审视。半晌，%1$s才缓缓开口：“是巧，还是有人算准了我会在这个时间出门？”

                对方的表情有一瞬间的松动。就在%1$s以为对方会否认时，对方却忽然笑了：“《%2$s》的主线已经开始了，%3$s题材的故事，从来都没有‘巧合’这两个字。”

                话音落下的瞬间，走廊里的灯忽然闪烁了两下。%1$s的心脏猛地一跳——不是因为对方的这句话，而是因为%1$s突然想起，自己昨晚在笔记最后一页写下的那句话：

                “不要相信任何主动接近你的人。”

                而现在，站在面前的这个人，正是昨夜之前%1$s最信任的人之一。

                “你……什么时候开始，连你也不可信了？”%1$s的声音有些发涩。

                对方没有回答，只是伸手从怀里取出一封信，递到%1$s面前：“看完它，你就明白了。然后，决定要不要信我。”

                信封上没有署名，只有一枚熟悉的火漆纹章。%1$s认得那枚纹章——那是十年前那个雨夜，%1$s亲眼看着它印在一封永远没有寄出去的信上。

                手指触碰到信封的瞬间，%1$s忽然感到一阵寒意顺着脊椎爬上来，像是有什么沉睡已久的东西，正在这封信里缓缓睁开眼睛。

                走廊尽头的窗户忽然被风吹开，风声呜呜作响，像是某种古老的警示。

                而%1$s，还没有来得及打开那封信。

                （本页为示例引擎生成的章节样张，接入真实模型后将按设定与大纲生成完整正文。）
                """, hero, title, genre);
    }

    private String continueText(AiContext ctx) {
        String hero = (ctx.characterNames() == null || ctx.characterNames().isEmpty())
                ? "他" : ctx.characterNames().get(0);
        return String.format("""
                夜色像一层浸了水的墨，缓缓压在窗棂上。%1$s没有点灯，只是静静坐着，任由方才那一幕在脑海里一遍又一遍地回放。

                有些事，一旦开始，就再没有回头的余地。%1$s垂下眼，指尖在桌面上轻轻叩了两下，像是在给某个迟迟未到的决定打着拍子。

                “罢了。”许久，%1$s低声开口，声音轻得几乎融进夜色里，语气却透出一股不容置疑的笃定。

                灯“啪”地亮了。昏黄的光晕里，%1$s的影子被拉得很长很长，一直延伸到门边——而那里，不知何时，多了一封没有署名的信。

                %1$s的瞳孔骤然一缩。信封上的火漆纹样，与记忆深处那个夜晚所见的，分毫不差。
                """, hero);
    }

    private String polish(AiContext ctx) {
        String src = blank(ctx.sourceText())
                ? "他推开那扇门，屋里很暗，有灰尘的味道。" : ctx.sourceText().trim();
        String[] paras = src.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            sb.append(t);
            if (t.endsWith("。") || t.endsWith("！") || t.endsWith("？")) {
                sb.append("\n");
            } else if (t.endsWith("，") || t.endsWith("、")) {
                sb.append("仿佛连空气都屏住了呼吸。\n");
            } else {
                sb.append("，仿佛连空气都屏住了呼吸。\n");
            }
        }
        sb.append("\n所有细碎的声响都退到了远处，只剩这一方天地，和天地间迟迟不肯落定的那一口气。");
        return sb.toString().trim();
    }

    private String expand(AiContext ctx) {
        String src = blank(ctx.sourceText())
                ? "雨下了一整夜，第二天清晨，街口的青石板泛着冷光。" : ctx.sourceText().trim();
        return String.format("""
                %1$s

                时间的流速仿佛在这一刻被放慢了。那些平日里被匆匆略过的细节——墙角剥落的漆皮、桌上凉透的半盏茶、窗外偶尔驶过的车灯扫亮又熄灭的光斑——忽然全都清晰起来，带着一种近乎固执的存在感。

                %1$s的目光扫过这些细枝末节，心绪却落在一个更远的地方。那里有一根看不见的线，一头拴在此刻，另一头拴在尚未揭晓的答案上。

                深吸一口气，再缓缓吐出。该来的总会来，而他要做的，只是在它到来之前，站到该站的位置上去。
                """, src);
    }

    private String chat(AiContext ctx) {
        String q = blank(ctx.sourceText()) ? "这个问题" : ctx.sourceText().trim();
        if (q.length() > 30) {
            q = q.substring(0, 30) + "……";
        }
        return String.format("""
                关于「%1$s」，给你三点具体建议：

                1. 剧情推进：先给主角一个三章内可达成的小目标，让他主动做事而不是被动应对——“正在变强、正在接近答案”的过程感，比结果更容易让读者代入。

                2. 期待感营造：在当前情节里埋一个信息差，要么“读者知道、主角不知道”，要么“主角知道、读者想知道”。钩子一旦立住，断章的位置就有了。

                3. 人物立体感：给主要配角安排一次与主线无关、却暴露其价值观的小选择。一句反常的话、一个反常的让步，往往比大段回忆更有效。

                把相关章节或人物设定发给我，我可以结合上下文给出更具体的方案。
                """, q);
    }

    private String summarize(AiContext ctx) {
        String src = blank(ctx.sourceText()) ? "本章尚无正文。" : ctx.sourceText().trim().replace("\n", " ");
        String head = src.length() > 160 ? src.substring(0, 160) + "……" : src;
        return String.format("""
                【本章摘要（示例）】
                %1$s

                （Mock 引擎仅生成占位摘要；接入真实模型后，将基于正文自动产出 150-200 字的准确摘要。）
                """, head);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
