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
            case CHAPTER_OUTLINE -> chapterOutline(ctx);
            case GUIDE_PROPOSAL -> guideProposals(ctx);
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

    private String chapterOutline(AiContext ctx) {
        String title = blank(ctx.novelTitle()) ? "未命名新书" : ctx.novelTitle();
        String lead = (ctx.characterNames() == null || ctx.characterNames().isEmpty())
                ? "主角" : ctx.characterNames().get(0);
        return String.format("""
                【本章大纲（示例）· 《%1$s》】
                1. 开篇钩子：以%3$s的一个细微异常切入，制造悬念，把读者拉进本章情境。
                2. 场景推进：在熟悉的场景里插入一次意外遭遇，迫使%3$s做出选择。
                3. 人物弧光：通过一次对话或内心独白，揭示%3$s隐藏的动机或软肋。
                4. 冲突升级：敌对势力/环境压力加码，把本章矛盾推到临界点。
                5. 收尾钩子：以一封信、一个消失的身影或一个未接的承诺收束，为下一章留悬念。

                （Mock 引擎仅生成占位大纲；接入真实模型后，将依据全书大纲与角色设定产出贴合的章节规划。）
                """, title, "", lead);
    }

    private String guideProposals(AiContext ctx) {
        String info = blank(ctx.sourceText()) ? "一个全新的故事想法" : ctx.sourceText();
        return """
                [
                  {
                    "title": "《余烬纪元》",
                    "premise": "（示例引擎）基于「%1$s」延展：末世灾变后文明在灰烬中重建，少年背负失落的传承，于废土上寻找真相。",
                    "world": "灾变后的分层世界：地表废土、地下避难所、悬浮天城三方势力犬牙交错。",
                    "protagonist": "沉默坚毅的拾荒少年，体内封印着旧时代的禁忌之力。",
                    "outline": "第一卷·拾荒者：从废墟中捡回一件改变命运的遗物。\\n第二卷·裂隙：三方势力围绕遗物展开追逐。\\n第三卷·余烬：揭开灾变真相，少年站上抉择之巅。",
                    "characters": [ {"name":"阿烬","role":"主角","description":"拾荒少年，坚韧善良，逐渐觉醒力量"}, {"name":"苏璃","role":"女主","description":"天城派来的观察者，立场微妙"} ],
                    "worlds": [ {"title":"废土地表","content":"辐射与遗迹并存的危险地带"}, {"title":"悬浮天城","content":"掌控旧时代科技的精英统治层"} ],
                    "platform": "起点 / 番茄"
                  },
                  {
                    "title": "《长安诡事录》",
                    "premise": "（示例引擎）基于「%1$s」延展：大唐长安诡秘案件接连发生，落魄贵族子弟以智破局，在权谋与玄奇间周旋。",
                    "world": "架空的盛唐气象，市井百态与朝堂暗涌交织，志怪元素若隐若现。",
                    "protagonist": "机敏毒舌的没落宗室，被迫卷入连环奇案。",
                    "outline": "第一卷·初案：一桩离奇命案牵出旧案。\\n第二卷·迷局：权贵联手设套。\\n第三卷·翻案：真相大白，主角逆风翻盘。",
                    "characters": [ {"name":"李妄","role":"主角","description":"没落宗室，智计百出"}, {"name":"裴缨","role":"搭档","description":"女捕快，武力担当"} ],
                    "worlds": [ {"title":"长安城","content":"繁华与暗影并存的都城"}, {"title":"玄机阁","content":"掌管志怪档案的隐秘机构"} ],
                    "platform": "晋江 / 自有书城"
                  },
                  {
                    "title": "《星轨设计师》",
                    "premise": "（示例引擎）基于「%1$s」延展：近未来普通人也能定制自己的星际航线，一名航线规划师在秩序与自由之间，写下浪漫而硬核的太空史诗。",
                    "world": "人类散落星海，航线即命运；星际公会、自由商人、AI 守门人三足鼎立。",
                    "protagonist": "孤独而浪漫的航线设计师，相信每一段旅程都该有温度。",
                    "outline": "第一卷·起航：接下一单不可能完成的航线。\\n第二卷·风暴：穿越 forbidden 星域。\\n第三卷·归处：在星海尽头找到属于自己的港。",
                    "characters": [ {"name":"言星","role":"主角","description":"航线设计师，温柔执着"}, {"name":"零","role":"AI","description":"随行智能体，逐渐拥有自我"} ],
                    "worlds": [ {"title":"星际公会","content":"垄断航线的权威组织"}, {"title":"自由星港","content":"商人与流浪者的集散地"} ],
                    "platform": "多平台同步"
                  }
                ]
                """.formatted(info.replace("%", "%%"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
