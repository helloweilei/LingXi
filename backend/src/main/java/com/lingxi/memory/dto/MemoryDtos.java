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

    /** 全书记忆视图里的单条大纲（按规划行展示，不依赖章节） */
    public record OutlineVO(Long outlineId, Integer chapterOrder, String chapterTitle,
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
     * 章节大纲列表项：**一行 = 一条规划**，不等同于章节。
     *
     * - outlineId：本条规划的标识（所有大纲操作都以它为准）
     * - chapterOrder / chapterTitle：规划序号与规划章节名
     * - chapterId：仅当该章正文已生成时才有值，否则为 null（规划先行于产物）
     * - wordCount：本条最终生效的目标字数（单章设定优先，否则全书均摊）
     * - wordCountCustomized：是否来自用户对本条的单独设定（前端据此提示"恢复默认"）
     */
    public record OutlineItemVO(Long outlineId, Integer chapterOrder, String chapterTitle,
                                Long chapterId, Long wordCount, boolean wordCountCustomized,
                                boolean hasContent, boolean hasOutline, String outline,
                                LocalDateTime updatedAt) {
    }

    /** 批量生成大纲请求：指定 outlineIds 则只生成这些；留空则生成全书尚无大纲内容的规划行 */
    public record GenerateReq(List<Long> outlineIds) {
    }

    /**
     * 手动新增一行章节大纲：这是"新建规划"，**不创建章节**。
     * 章节会在用户点「生成正文」时，才由这条规划落地产生。
     */
    public record CreateOutlineReq(
            @Size(max = 120, message = "章节名最长 120 字") String chapterTitle,
            @Size(max = 2000, message = "大纲最长 2000 字") String outline,
            @Min(value = 100, message = "本章目标字数最少 100 字")
            @Max(value = 20000, message = "本章目标字数最多 20000 字") Integer targetWords) {
    }

    /** 手动编辑一条规划；targetWords 为 null 表示保持原有字数设定不变 */
    public record OutlineReq(
            @Size(max = 120, message = "章节名最长 120 字") String chapterTitle,
            @Size(max = 2000, message = "大纲最长 2000 字") String outline,
            @Min(value = 100, message = "本章目标字数最少 100 字")
            @Max(value = 20000, message = "本章目标字数最多 20000 字") Integer targetWords) {
    }

    /** 单独设置某条规划的目标字数：words 为 null 或 &lt;=0 表示恢复默认（全书均摊） */
    public record ChapterWordsReq(
            @Min(value = 0, message = "字数不能为负数")
            @Max(value = 20000, message = "本章目标字数最多 20000 字") Integer words) {
    }
}
