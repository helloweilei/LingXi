package com.lingxi.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public final class WritingTaskDtos {

    private WritingTaskDtos() {
    }

    /** 发起自动创作请求（收集用户创作意图） */
    public record AutoWriteReq(
            @Pattern(regexp = "AUTO|CONFIRM", message = "mode 仅支持 AUTO 或 CONFIRM") String mode,
            @Min(value = 1, message = "章节数最少 1 章") @Max(value = 50, message = "单次最多 50 章") Integer chapters,
            @Min(value = 300, message = "每章字数最少 300 字") @Max(value = 5000, message = "每章字数最多 5000 字") Integer wordsPerChapter,
            Boolean genOutline,
            Boolean autoSummary) {
    }

    /** 任务视图（不含敏感信息） */
    public record TaskVO(Long id, Long novelId, String mode, String status,
                         Integer totalChapters, Integer currentChapter, String currentTitle,
                         Integer chapters, Integer wordsPerChapter, Boolean genOutline, Boolean autoSummary,
                         String error, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
