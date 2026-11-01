package com.lingxi.ai;

import com.lingxi.character.NovelCharacter;
import com.lingxi.config.AiProperties;
import com.lingxi.memory.ChapterOutline;
import com.lingxi.memory.StoryMemory;
import com.lingxi.novel.Novel;
import com.lingxi.world.WorldSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文构建器骨架测试：预算约束、优先级保留、一致性卡片。
 */
class ContextBuilderTest {

    private final AiProperties props = new AiProperties();
    private final ContextBuilder builder = new ContextBuilder(props);

    private Novel novel() {
        Novel n = new Novel();
        n.setTitle("星辰之下");
        n.setGenre("都市幻想");
        n.setDescription("少年觉醒异能，卷入暗流。");
        return n;
    }

    @Test
    void emptyDataProducesInfoOnlyWithoutError() {
        String base = builder.buildBase(novel(), List.of(), List.of(), null, List.of());
        assertThat(base).contains("书名：星辰之下").contains("都市幻想");
    }

    @Test
    void consistencyCardContainsRosterAndWorld() {
        NovelCharacter c = new NovelCharacter();
        c.setName("林夜");
        c.setRole("主角");
        c.setDescription("冷静坚韧，异能觉醒者");
        WorldSetting w = new WorldSetting();
        w.setTitle("源能体系");
        w.setContent("世界能量来源，等级划分明确");
        String card = builder.buildConsistencyCard(List.of(c), List.of(w));
        assertThat(card).contains("林夜").contains("主角").contains("源能体系");
    }

    @Test
    void longOutlineIsTrimmedWithinBudget() {
        Novel n = novel();
        n.setOutline("大纲内容".repeat(3000));
        String base = builder.buildBase(n, List.of(), List.of(), null, List.of());
        assertThat(base.length()).isLessThanOrEqualTo(props.getContextBudgetChars());
        // 保头尾：省略号出现说明发生截断，且首尾关键内容仍在
        assertThat(base).contains("……（略）……");
    }

    @Test
    void outlinesLimitedToRecentCount() {
        List<ChapterOutline> outlines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ChapterOutline o = new ChapterOutline();
            o.setOutline("第" + i + "章大纲内容");
            outlines.add(o);
        }
        String base = builder.buildBase(novel(), List.of(), List.of(), null, outlines);
        int count = 0;
        for (String line : base.split("\n")) {
            if (line.startsWith("- 第") && line.endsWith("章大纲内容")) {
                count++;
            }
        }
        assertThat(count).isEqualTo(props.getMaxRecentSummaries());
    }

    @Test
    void storyBrainIsIncludedWhenPresent() {
        StoryMemory memory = new StoryMemory();
        memory.setStoryBrain("主角已发现组织阴谋，玉佩伏笔尚未回收。");
        String base = builder.buildBase(novel(), List.of(), List.of(), memory, List.of());
        assertThat(base).contains("故事脑").contains("玉佩伏笔尚未回收");
    }

    @Test
    void openForeshadowingsAreInjectedWithChapterHints() {
        com.lingxi.memory.Foreshadowing open = new com.lingxi.memory.Foreshadowing();
        open.setTitle("师父的身世之谜");
        open.setContent("第 3 章师父留下的半块玉佩，回收时须揭示其与魔教的关系");
        open.setPlantedOrder(3);
        open.setExpectedOrder(12);
        open.setStatus(com.lingxi.memory.Foreshadowing.STATUS_OPEN);
        com.lingxi.memory.Foreshadowing resolved = new com.lingxi.memory.Foreshadowing();
        resolved.setTitle("已回收的旧伏笔");
        resolved.setStatus(com.lingxi.memory.Foreshadowing.STATUS_RESOLVED);
        String base = builder.buildBase(novel(), List.of(), List.of(), null, List.of(),
                List.of(open, resolved));
        // OPEN 条目必须在场（长程悬念始终可见），并带埋设/预期回收章提示
        assertThat(base).contains("未回收伏笔").contains("师父的身世之谜").contains("玉佩")
                .contains("埋于第3章").contains("预期第12章前后回收");
        // 已回收条目不再注入，避免占预算、误导模型
        assertThat(base).doesNotContain("已回收的旧伏笔");
    }
}
