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
            String coverColor,
            String languageStyle) {
    }

    /**
     * 作品更新请求（部分更新语义：null 表示该字段不变）。
     *
     * 注意：record 是位置参数，新增字段会让所有 {@code new UpdateReq(...)} 调用点错位，
     * 因此禁止在代码里直接用全参构造器——一律通过 {@link #builder()} 链式设置需要的字段。
     */
    public record UpdateReq(
            @Size(max = 80) String title,
            @Size(max = 1000) String description,
            @Size(max = 100) String genre,
            @Size(max = 20) String status,
            Integer targetWords,
            String coverColor,
            @Size(max = 500) String coverUrl,
            @Size(max = 2000, message = "封面提示词最长 2000 字") String coverPrompt,
            @Size(max = 30, message = "作者名最长 30 字") String authorName,
            @Size(max = 30000) String outline,
            String languageStyle) {

        public static Builder builder() {
            return new Builder();
        }

        /** 链式构建器：新增字段只需在此处补一行，调用点无需改动 */
        public static final class Builder {
            private String title, description, genre, status, coverColor, coverUrl, coverPrompt;
            private Integer targetWords;
            private String authorName, outline, languageStyle;

            public Builder title(String v) {
                this.title = v;
                return this;
            }

            public Builder description(String v) {
                this.description = v;
                return this;
            }

            public Builder genre(String v) {
                this.genre = v;
                return this;
            }

            public Builder status(String v) {
                this.status = v;
                return this;
            }

            public Builder targetWords(Integer v) {
                this.targetWords = v;
                return this;
            }

            public Builder coverColor(String v) {
                this.coverColor = v;
                return this;
            }

            public Builder coverUrl(String v) {
                this.coverUrl = v;
                return this;
            }

            public Builder coverPrompt(String v) {
                this.coverPrompt = v;
                return this;
            }

            public Builder authorName(String v) {
                this.authorName = v;
                return this;
            }

            public Builder outline(String v) {
                this.outline = v;
                return this;
            }

            public Builder languageStyle(String v) {
                this.languageStyle = v;
                return this;
            }

            public UpdateReq build() {
                return new UpdateReq(title, description, genre, status, targetWords, coverColor,
                        coverUrl, coverPrompt, authorName, outline, languageStyle);
            }
        }
    }

    public record VO(Long id, String title, String description, String genre, String status,
                     Integer targetWords, Long wordCount, String coverColor, String coverUrl, String languageStyle,
                     Long chapterCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record DetailVO(Long id, String title, String description, String genre, String status,
                           Integer targetWords, Long wordCount, String coverColor, String coverUrl, String outline,
                           String languageStyle, String coverPrompt, String authorName,
                           Long chapterCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
