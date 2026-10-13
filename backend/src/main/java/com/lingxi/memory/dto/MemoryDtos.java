package com.lingxi.memory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class MemoryDtos {

    private MemoryDtos() {
    }

    public record OutlineVO(Long chapterId, String chapterTitle, Integer chapterOrder,
                            String outline, LocalDateTime updatedAt) {
    }

    public record StoryBrainReq(
            @NotBlank(message = "故事脑内容不能为空")
            @Size(max = 20000, message = "故事脑最长 20000 字") String storyBrain) {
    }

    public record MemoryVO(Long novelId, List<OutlineVO> outlines, String storyBrain,
                           LocalDateTime storyBrainUpdatedAt) {
    }

    /**
     * 章节大纲列表项：每个章节一行，含是否生成大纲、是否已写正文。
     * wordCount 为该章最终生效的目标字数（单章设定优先，否则全书均摊）；
     * wordCountCustomized 标记它是否来自用户对本章的单独设定（前端据此提示可"恢复默认"）。
     */
    public record OutlineItemVO(Long chapterId, Integer chapterOrder, String chapterTitle,
                                Long wordCount, boolean wordCountCustomized,
                                boolean hasContent, boolean hasOutline, String outline,
                                LocalDateTime updatedAt) {
    }

    /** 一键/批量生成大纲请求：指定章节则只生成这些，留空则生成全书未生成大纲的章节（不会创建章节） */
    public record GenerateReq(List<Long> chapterIds) {
    }

    /** 手动新增 / 编辑单章大纲；targetWords 为 null 表示沿用全书均摊值 */
    public record OutlineReq(
            @NotBlank(message = "大纲内容不能为空")
            @Size(max = 2000, message = "大纲最长 2000 字") String outline,
            @Min(value = 100, message = "本章目标字数最少 100 字")
            @Max(value = 20000, message = "本章目标字数最多 20000 字") Integer targetWords) {
    }

    /** 单独设置某章目标字数：words 为 null 或 &lt;=0 表示恢复默认（全书均摊） */
    public record ChapterWordsReq(
            @Min(value = 0, message = "字数不能为负数")
            @Max(value = 20000, message = "本章目标字数最多 20000 字") Integer words) {
    }
}
