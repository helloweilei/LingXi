package com.lingxi.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class MemoryDtos {

    private MemoryDtos() {
    }

    public record SummaryVO(Long chapterId, String chapterTitle, Integer chapterOrder,
                            String summary, LocalDateTime updatedAt) {
    }

    public record StoryBrainReq(
            @NotBlank(message = "故事脑内容不能为空")
            @Size(max = 20000, message = "故事脑最长 20000 字") String storyBrain) {
    }

    public record MemoryVO(Long novelId, List<SummaryVO> summaries, String storyBrain,
                           LocalDateTime storyBrainUpdatedAt) {
    }
}
