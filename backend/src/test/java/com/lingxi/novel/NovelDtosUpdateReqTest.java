package com.lingxi.novel;

import com.lingxi.novel.dto.NovelDtos.UpdateReq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 作品更新请求的 Builder 行为。
 *
 * 背景：UpdateReq 曾因"新增字段导致位置参数错位"多次引发编译失败，
 * 故约定一律通过 builder() 构造，这里用测试固化该约定。
 */
class NovelDtosUpdateReqTest {

    @Test
    void builderOnlySetsGivenFields() {
        UpdateReq req = UpdateReq.builder()
                .outline("全书大纲")
                .build();
        assertEquals("全书大纲", req.outline());
        // 其余字段必须为 null（部分更新语义：不设置即不变更）
        assertNull(req.title());
        assertNull(req.description());
        assertNull(req.genre());
        assertNull(req.status());
        assertNull(req.targetWords());
        assertNull(req.coverColor());
        assertNull(req.coverUrl());
        assertNull(req.coverPrompt());
        assertNull(req.authorName());
        assertNull(req.languageStyle());
    }

    @Test
    void builderSupportsCoverAndAuthorFields() {
        UpdateReq req = UpdateReq.builder()
                .coverUrl("/uploads/a.png")
                .coverPrompt("封面提示词")
                .authorName("墨云生")
                .build();
        assertEquals("/uploads/a.png", req.coverUrl());
        assertEquals("封面提示词", req.coverPrompt());
        assertEquals("墨云生", req.authorName());
        assertNull(req.outline());
    }

    @Test
    void languageStyleCanBeClearedWithEmptyString() {
        UpdateReq req = UpdateReq.builder().languageStyle("").build();
        assertEquals("", req.languageStyle());
    }
}