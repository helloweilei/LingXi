package com.lingxi.chapter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ChapterDtos {

    private ChapterDtos() {
    }

    public record SaveReq(
            @Size(max = 120, message = "章节名最长 120 字") String title,
            @Size(max = 200000, message = "单章内容过长") String content,
            @Size(max = 30, message = "快照来源最长 30 字") String reason) {
    }

    /** 批量删除章节请求 */
    public record BatchDeleteReq(@NotEmpty(message = "请选择要删除的章节") List<Long> ids) {
    }

    public record MetaVO(Long id, Long novelId, String title, Integer chapterOrder, Long wordCount,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record VO(Long id, Long novelId, String title, Integer chapterOrder, Long wordCount,
                     String content, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record VersionVO(Long id, Integer versionNo, String title, Long wordCount,
                            String reason, LocalDateTime createdAt) {
    }
}
