package com.lingxi.novel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class NovelDtos {

    private NovelDtos() {
    }

    public record CreateReq(
            @NotBlank(message = "书名不能为空") @Size(max = 80, message = "书名最长 80 字") String title,
            @Size(max = 1000) String description,
            @Size(max = 100) String genre,
            Integer targetWords,
            String coverColor) {
    }

    public record UpdateReq(
            @Size(max = 80) String title,
            @Size(max = 1000) String description,
            @Size(max = 100) String genre,
            @Size(max = 20) String status,
            Integer targetWords,
            String coverColor,
            @Size(max = 30000) String outline) {
    }

    public record VO(Long id, String title, String description, String genre, String status,
                     Integer targetWords, Long wordCount, String coverColor, Long chapterCount,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record DetailVO(Long id, String title, String description, String genre, String status,
                           Integer targetWords, Long wordCount, String coverColor, String outline,
                           Long chapterCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
