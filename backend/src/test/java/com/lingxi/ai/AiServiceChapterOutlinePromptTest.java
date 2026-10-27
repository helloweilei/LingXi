package com.lingxi.ai;

import com.lingxi.chapter.ChapterRepository;
import com.lingxi.chapter.ChapterService;
import com.lingxi.character.CharacterRepository;
import com.lingxi.config.AiProperties;
import com.lingxi.memory.ChapterOutline;
import com.lingxi.memory.ChapterOutlineRepository;
import com.lingxi.memory.StoryMemoryRepository;
import com.lingxi.novel.Novel;
import com.lingxi.novel.NovelService;
import com.lingxi.world.WorldSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 章节大纲 prompt 的关键约束（历史 bug 回归）：
 * 1. 全书大纲（novel.outline，含结局/完结描述）必须进 prompt——
 *    此前只带「作品信息」却要求“与全书大纲一致”，模型看不到大纲，
 *    一键生成的最后一章大纲没有任何完结规划；
 * 2. 上一章大纲必须进 prompt——章节是连续叙事，缺了直接上文，
 *    生成的规划容易重复已写事件、丢掉上文悬念或与上文状态断裂；
 * 3. 终章必须带“完结规划”指令，且禁止再留下一章钩子；
 * 4. 非终章保留钩子规则，不带完结块。
 */
class AiServiceChapterOutlinePromptTest {

    private final ChapterOutlineRepository outlineRepo = mock(ChapterOutlineRepository.class);

    private final AiService aiService = new AiService(
            mock(LlmClientProvider.class),
            mock(NovelService.class),
            mock(ChapterRepository.class),
            mock(ChapterService.class),
            mock(CharacterRepository.class),
            mock(WorldSettingRepository.class),
            outlineRepo,
            mock(StoryMemoryRepository.class),
            new ContextBuilder(new AiProperties()));

    private static Novel novelWithOutline(String outline) {
        Novel n = new Novel();
        n.setTitle("灵犀测试");
        n.setGenre("玄幻");
        n.setDescription("测试用作品");
        n.setOutline(outline);
        return n;
    }

    private static ChapterOutline outline(int order) {
        ChapterOutline o = new ChapterOutline();
        o.setNovelId(1L);
        o.setChapterOrder(order);
        o.setOutline("");
        return o;
    }

    @Test
    void promptCarriesBookOutlineIncludingItsEnding() {
        Novel novel = novelWithOutline(
                "第一卷：少年觉醒。\n第二卷：踏入宗门。\n结局卷：终局之战，主角封印魔王，与伙伴们重建家园，全书完结。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(3), false, null, null);

        assertTrue(prompt.contains("【全书大纲"));
        assertTrue(prompt.contains("少年觉醒"));
        assertTrue(prompt.contains("终局之战"));
        assertTrue(prompt.contains("全书完结"));
        // 全书大纲取尾部：结局描述必须存活（不被头中段省略吃掉）
        assertFalse(prompt.contains("……（略）……"));
    }

    @Test
    void promptCarriesPreviousOutlineWithContinuityRules() {
        Novel novel = novelWithOutline("第一卷：少年觉醒。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(4), false, null,
                "第三章：林渊在秘境中获得灵犀碎片，仇家赵屠循迹追至谷口，章末以夜袭警报收尾。");

        assertTrue(prompt.contains("【上一章大纲"));
        assertTrue(prompt.contains("本章的直接上文"));
        assertTrue(prompt.contains("灵犀碎片"));
        assertTrue(prompt.contains("夜袭警报"));
        // 衔接要求：承接上文状态、不重复、推进上文悬念
        assertTrue(prompt.contains("【衔接要求"));
        assertTrue(prompt.contains("不得重新开场"));
        assertTrue(prompt.contains("上一章留下的悬念"));
        assertTrue(prompt.contains("章节名不得与上一章标题雷同"));
    }

    @Test
    void firstChapterPromptHasNoPreviousOutlineBlock() {
        Novel novel = novelWithOutline("第一卷：少年觉醒。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(1), false, null, null);

        // 第一章没有上一章：不注入块，也不给衔接要求
        assertFalse(prompt.contains("【上一章大纲"));
        assertFalse(prompt.contains("【衔接要求"));
        // 其余规则不受影响
        assertTrue(prompt.contains("再为下一章留一个自然的悬念"));
    }

    @Test
    void blankPreviousOutlineIsSkipped() {
        Novel novel = novelWithOutline("第一卷：少年觉醒。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(4), false, null, "  \n ");

        assertFalse(prompt.contains("【上一章大纲"));
        assertFalse(prompt.contains("【衔接要求"));
    }

    @Test
    void finalChapterPromptHasEndingPlanning() {
        Novel novel = novelWithOutline("第一卷：崛起。\n结局：主角成为灵犀之主，一切尘埃落定。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(10), true, null, null);

        assertTrue(prompt.contains("全书最后一章"));
        assertTrue(prompt.contains("完结规划"));
        assertTrue(prompt.contains("最终结果或答案"));
        assertTrue(prompt.contains("成长弧光有明确归宿"));
        assertTrue(prompt.contains("严禁再预留引向下一章的钩子"));
        // 终章头部必须标注“（全书最后一章）”
        assertTrue(prompt.contains("（全书最后一章）"));
    }

    @Test
    void nonFinalChapterPromptKeepsHookRuleAndHasNoEndingBlock() {
        Novel novel = novelWithOutline("第一卷：崛起，埋下灵犀之谜。");
        String prompt = aiService.buildChapterOutlinePrompt(novel, outline(3), false, null, null);

        assertTrue(prompt.contains("再为下一章留一个自然的悬念"));
        assertTrue(prompt.contains("严格遵循上方“全书大纲”"));
        assertFalse(prompt.contains("完结规划（最高优先级）"));
        // 头部不得标注为终章（任务指令里的“若不是全书最后一章”条件句除外）
        assertFalse(prompt.contains("（全书最后一章）"));
    }

    @Test
    void isFinalPlannedChapterUsesTotalChapters() {
        // 自动流水线：边建规划行边生成，只有显式总数能识别终章
        assertTrue(AiService.isFinalPlannedChapter(outline(10), 10));
        assertFalse(AiService.isFinalPlannedChapter(outline(9), 10));
        // 一键从零规划：先建满 N 行，按行数推断时最后一章即终章
        assertTrue(AiService.isFinalPlannedChapter(outline(1), 1));
        // 无总数信息时不误判
        assertFalse(AiService.isFinalPlannedChapter(outline(3), 0));
        assertFalse(AiService.isFinalPlannedChapter(null, 10));
    }

    @Test
    void previousOutlineTextPicksClosestLowerOrderAndSkipsBlank() {
        // 序号允许缺口：实际“上一行”由仓库派生查询按最大更小序号取回，这里只验证委派与兜底
        ChapterOutline row = new ChapterOutline();
        row.setNovelId(1L);
        row.setChapterOrder(4);
        row.setOutline("第四章：林渊拜入宗门，结识阿沅。");
        when(outlineRepo.findTopByNovelIdAndChapterOrderLessThanOrderByChapterOrderDesc(1L, 5))
                .thenReturn(Optional.of(row));

        assertEquals("第四章：林渊拜入宗门，结识阿沅。", aiService.previousOutlineText(1L, 5));
        // 第一章：无上一章（Mockito 对 Optional 返回类型默认返回 empty）
        assertNull(aiService.previousOutlineText(1L, 1));
        // 序号为空时不查库，直接视为没有上一章
        assertNull(aiService.previousOutlineText(1L, null));

        // 上一章规划内容为空白：视为没有上一章大纲，不注入空块
        ChapterOutline blank = new ChapterOutline();
        blank.setNovelId(2L);
        blank.setChapterOrder(4);
        blank.setOutline("  \n ");
        when(outlineRepo.findTopByNovelIdAndChapterOrderLessThanOrderByChapterOrderDesc(2L, 5))
                .thenReturn(Optional.of(blank));
        assertNull(aiService.previousOutlineText(2L, 5));
    }
}
