package com.lingxi.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 章节名处理规则：
 * 1. normalizeTitle —— 清洗 AI 返回的标题（去书名号/序号前缀/尾部标点）
 * 2. isPlaceholderTitle —— 判定「未命名章节 / 第1章」这类占位名（决定是否允许 AI 命名覆盖）
 */
class AiServiceChapterTitleTest {

    @Test
    void normalizeStripsQuotesAndPunctuation() {
        assertEquals("雨夜惊变", AiService.normalizeTitle("雨夜惊变"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("《雨夜惊变》"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("「雨夜惊变」"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("\"雨夜惊变\""));
        assertEquals("雨夜惊变", AiService.normalizeTitle("雨夜惊变。"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("雨夜惊变\n"));
    }

    @Test
    void normalizeStripsChapterNumberPrefix() {
        assertEquals("雨夜惊变", AiService.normalizeTitle("第一章：雨夜惊变"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("第3章 雨夜惊变"));
        assertEquals("雨夜惊变", AiService.normalizeTitle("第 12 回：雨夜惊变"));
    }

    @Test
    void normalizeReturnsNullWhenEmpty() {
        assertNull(AiService.normalizeTitle(null));
        assertNull(AiService.normalizeTitle(""));
        assertNull(AiService.normalizeTitle("   "));
        assertNull(AiService.normalizeTitle("《》"));
    }

    @Test
    void normalizeTruncatesOverlongTitle() {
        String long40 = "一".repeat(60);
        assertEquals(40, AiService.normalizeTitle(long40).length());
    }

    @Test
    void detectsPlaceholderTitles() {
        assertTrue(AiService.isPlaceholderTitle(null));
        assertTrue(AiService.isPlaceholderTitle(""));
        assertTrue(AiService.isPlaceholderTitle("  "));
        assertTrue(AiService.isPlaceholderTitle("未命名章节"));
        assertTrue(AiService.isPlaceholderTitle("待定"));
        assertTrue(AiService.isPlaceholderTitle("第1章"));
        assertTrue(AiService.isPlaceholderTitle("第 12 回"));
        assertTrue(AiService.isPlaceholderTitle("3"));
    }

    @Test
    void keepsUserProvidedTitle() {
        assertFalse(AiService.isPlaceholderTitle("雨夜惊变"));
        assertFalse(AiService.isPlaceholderTitle("第一章：觉醒"));
        assertFalse(AiService.isPlaceholderTitle("她的第一次反击"));
    }
}