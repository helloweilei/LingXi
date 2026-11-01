package com.lingxi.ai;

import com.lingxi.character.NovelCharacter;
import com.lingxi.config.AiProperties;
import com.lingxi.memory.ChapterOutline;
import com.lingxi.memory.Foreshadowing;
import com.lingxi.memory.StoryMemory;
import com.lingxi.novel.Novel;
import com.lingxi.world.WorldSetting;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文构建器：把"作品信息 → 一致性卡片 → 全书大纲 → 故事脑 → 已规划章节大纲"
 * 按优先级打包进设定的字符预算（中文约 1 字 ≈ 1 token）。
 * 预算耗尽时按优先级从低到高丢弃/压缩，保证核心设定始终在场。
 */
@Component
public class ContextBuilder {

    /** 各段独立预算（字符），总和 ≤ 默认总预算 8000 */
    private static final int INFO_BUDGET = 300;
    private static final int CARD_BUDGET = 1200;
    /** 未回收伏笔清单：长程悬念的唯一可靠载体，优先级高于全书大纲（被压缩时优先保它） */
    private static final int FORESHADOW_BUDGET = 800;
    private static final int OUTLINE_BUDGET = 2800;
    private static final int BRAIN_BUDGET = 1600;
    private static final int OUTLINE_CHAIN_BUDGET = 1600;
    private static final int OUTLINE_CHAIN_ENTRY_BUDGET = 300;
    private static final int CARD_DESC_BUDGET = 80;
    private static final int CARD_WORLD_BUDGET = 120;
    private static final int FORESHADOW_ENTRY_BUDGET = 120;
    private static final String ELLIPSIS = "……（略）……";

    private final int totalBudget;
    private final int maxRecentOutlines;

    public ContextBuilder(AiProperties props) {
        this.totalBudget = Math.max(1000, props.getContextBudgetChars());
        this.maxRecentOutlines = Math.max(1, props.getMaxRecentSummaries());
    }

    /** 作品信息 + 大纲（历史兼容入口，供单任务使用） */
    public String buildNovelInfo(Novel novel) {
        return buildInfo(novel);
    }

    /** 按优先级打包全部静态上下文段（历史兼容入口：不含伏笔清单） */
    public String buildBase(Novel novel, List<NovelCharacter> characters, List<WorldSetting> worlds,
                            StoryMemory memory, List<ChapterOutline> outlines) {
        return buildBase(novel, characters, worlds, memory, outlines, null);
    }

    /**
     * 同上，支持未回收伏笔清单：OPEN 条目作为独立段注入，
     * 使超过前文回顾窗口（约 3 章）的长程悬念在生成时始终在场。
     */
    public String buildBase(Novel novel, List<NovelCharacter> characters, List<WorldSetting> worlds,
                            StoryMemory memory, List<ChapterOutline> outlines,
                            List<Foreshadowing> foreshadowings) {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section(buildInfo(novel), INFO_BUDGET));
        sections.add(new Section(buildConsistencyCard(characters, worlds), CARD_BUDGET));
        sections.add(new Section(buildForeshadowings(foreshadowings), FORESHADOW_BUDGET));
        sections.add(new Section(buildOutline(novel), OUTLINE_BUDGET));
        sections.add(new Section(buildBrain(memory), BRAIN_BUDGET));
        sections.add(new Section(buildOutlines(outlines), OUTLINE_CHAIN_BUDGET));

        StringBuilder sb = new StringBuilder();
        int remaining = totalBudget;
        for (Section s : sections) {
            if (s.text.isBlank() || remaining <= 0) {
                continue;
            }
            String fitted = trimMiddle(s.text, Math.min(s.budget, remaining));
            if (fitted.isBlank()) {
                continue;
            }
            sb.append(fitted).append("\n");
            remaining -= fitted.length();
        }
        return sb.toString().trim();
    }

    /** 一致性卡片：角色名册 + 世界观要点（规则版，无需 LLM） */
    public String buildConsistencyCard(List<NovelCharacter> characters, List<WorldSetting> worlds) {
        StringBuilder sb = new StringBuilder();
        if (characters != null && !characters.isEmpty()) {
            sb.append("【角色名册】\n");
            for (NovelCharacter c : characters) {
                sb.append("- ").append(c.getName());
                if (c.getRole() != null && !c.getRole().isBlank()) {
                    sb.append("（").append(c.getRole()).append("）");
                }
                if (c.getDescription() != null && !c.getDescription().isBlank()) {
                    sb.append("：").append(trimTail(c.getDescription(), CARD_DESC_BUDGET));
                }
                sb.append("\n");
            }
        }
        if (worlds != null && !worlds.isEmpty()) {
            sb.append("【世界观要点】\n");
            for (WorldSetting w : worlds) {
                sb.append("- ").append(w.getTitle()).append("：")
                        .append(trimTail(w.getContent(), CARD_WORLD_BUDGET)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String buildInfo(Novel novel) {
        StringBuilder sb = new StringBuilder("【作品信息】\n书名：").append(novel.getTitle())
                .append("\n类型：").append(orDefault(novel.getGenre(), "未设置"))
                .append("\n简介：").append(orDefault(novel.getDescription(), "（暂无）"));
        if (novel.getLanguageStyle() != null && !novel.getLanguageStyle().isBlank()) {
            sb.append("\n语言风格：").append(novel.getLanguageStyle());
        }
        return sb.toString();
    }

    private String buildOutline(Novel novel) {
        if (novel.getOutline() == null || novel.getOutline().isBlank()) {
            return "";
        }
        return "【全书大纲】\n" + novel.getOutline();
    }

    /**
     * 未回收伏笔清单：只含 OPEN 条目（RESOLVED/ABANDONED 不注入，避免占预算、误导模型）。
     * 每条带埋设章序与预期回收章，模型可据此在合适章节安排承接与回收。
     */
    private String buildForeshadowings(List<Foreshadowing> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【未回收伏笔（规划与写作时优先承接，到达合适章节应回收，不得凭空遗忘或自相矛盾）】\n");
        for (Foreshadowing f : items) {
            // 构建器层兜底过滤：即便调用方传入了非 OPEN 条目也不注入
            if (!Foreshadowing.STATUS_OPEN.equals(f.getStatus())) {
                continue;
            }
            sb.append("- ").append(f.getTitle());
            if (f.getContent() != null && !f.getContent().isBlank()) {
                sb.append("：").append(trimTail(f.getContent(), FORESHADOW_ENTRY_BUDGET));
            }
            if (f.getPlantedOrder() != null) {
                sb.append("（埋于第").append(f.getPlantedOrder()).append("章");
                if (f.getExpectedOrder() != null) {
                    sb.append("，预期第").append(f.getExpectedOrder()).append("章前后回收");
                }
                sb.append("）");
            } else if (f.getExpectedOrder() != null) {
                sb.append("（预期第").append(f.getExpectedOrder()).append("章前后回收）");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildBrain(StoryMemory memory) {
        if (memory == null || memory.getStoryBrain() == null || memory.getStoryBrain().isBlank()) {
            return "";
        }
        return "【故事脑：已发生的关键剧情与未回收伏笔】\n" + memory.getStoryBrain();
    }

    private String buildOutlines(List<ChapterOutline> outlines) {
        if (outlines == null || outlines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【已规划章节大纲】\n");
        int from = Math.max(0, outlines.size() - maxRecentOutlines);
        for (int i = from; i < outlines.size(); i++) {
            ChapterOutline o = outlines.get(i);
            sb.append("- ").append(trimTail(o.getOutline(), OUTLINE_CHAIN_ENTRY_BUDGET)).append("\n");
        }
        return sb.toString().trim();
    }

    /** 预算内截断：优先保头尾（结构信息与最新状态），中间以省略号压缩 */
    private static String trimMiddle(String text, int budget) {
        if (text == null || text.length() <= budget) {
            return text == null ? "" : text;
        }
        if (budget <= ELLIPSIS.length() + 2) {
            return text.substring(0, budget);
        }
        int head = (budget - ELLIPSIS.length()) * 3 / 5;
        int tail = budget - ELLIPSIS.length() - head;
        return text.substring(0, head) + ELLIPSIS + text.substring(text.length() - tail);
    }

    private static String trimTail(String text, int max) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Section(String text, int budget) {
    }
}
