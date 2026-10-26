package com.lingxi.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 引导问答「篇幅/字数」→ 全书目标字数的推导规则。
 * 重点：用户可填写确切字数（而非只能选短/中/长粗档）。
 */
class IdeaGuideTargetWordsTest {

    @Test
    void parsesExactArabicNumbers() {
        assertEquals(500_000, IdeaGuideService.deriveTarget("50万"));
        assertEquals(1_000_000, IdeaGuideService.deriveTarget("100万字"));
        assertEquals(120_000, IdeaGuideService.deriveTarget("12w"));
        assertEquals(30_000, IdeaGuideService.deriveTarget("3万"));
        assertEquals(2_000_000, IdeaGuideService.deriveTarget("200万"));
    }

    @Test
    void plainNumberIsTakenAsWords() {
        // 无单位时按字面理解；低于 1 万下限会被收敛到 1 万（见 outOfRangeIsClamped）
        assertEquals(15_000, IdeaGuideService.deriveTarget("15000字"));
        assertEquals(10_000, IdeaGuideService.deriveTarget("8000字"));
    }

    @Test
    void rangeTakesUpperBound() {
        // 「30-50万」应取上限 50 万，而不是先匹配到的 30 万
        assertEquals(500_000, IdeaGuideService.deriveTarget("中篇30-50万"));
    }

    @Test
    void parsesChineseNumerals() {
        assertEquals(200_000, IdeaGuideService.deriveTarget("二十万字"));
        assertEquals(300_000, IdeaGuideService.deriveTarget("三十万"));
    }

    @Test
    void fallsBackToLengthBuckets() {
        // 无确切数字时才按粗档回落
        assertEquals(100_000, IdeaGuideService.deriveTarget("短篇"));
        assertEquals(1_000_000, IdeaGuideService.deriveTarget("长篇"));
        assertEquals(300_000, IdeaGuideService.deriveTarget("中篇"));
        assertEquals(300_000, IdeaGuideService.deriveTarget("连载不限"));
        assertEquals(300_000, IdeaGuideService.deriveTarget(null));
        assertEquals(300_000, IdeaGuideService.deriveTarget("随便写写"));
    }

    @Test
    void bucketOptionsWithDigitsPreferExplicitNumber() {
        // 预置选项本身含数字时，以最明确的那个数值为准（「中篇30-50万」→ 50万）
        assertEquals(500_000, IdeaGuideService.deriveTarget("中篇30-50万"));
        assertEquals(100_000, IdeaGuideService.deriveTarget("短篇10万字内"));
    }

    @Test
    void outOfRangeIsClamped() {
        // 过小/过大的输入被收敛到 1 万 ~ 1000 万
        assertEquals(10_000, IdeaGuideService.deriveTarget("10字"));
        assertEquals(10_000_000, IdeaGuideService.deriveTarget("9999万"));
    }

    @Test
    void doesNotMisreadWordsWithoutUnitContext() {
        // 「十二生肖」这类不应被当成字数 → 回落默认
        assertEquals(300_000, IdeaGuideService.deriveTarget("十二生肖"));
    }
}