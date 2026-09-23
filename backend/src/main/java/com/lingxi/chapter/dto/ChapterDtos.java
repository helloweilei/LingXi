package com.lingxi.chapter.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ChapterDtos {

    private ChapterDtos() {
    }

    public record SaveReq(
            @Size(max = 120, message = "章节名最长 120 字") String title,
            @Size(max = 200000, message = "单章内容过长") String content) {
    }

    public record MetaVO(Long id, Long novelId, String title, Integer chapterOrder, Long wordCount,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record VO(Long id, Long novelId, String title, Integer chapterOrder, Long wordCount,
                     String content, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
