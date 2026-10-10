package com.lingxi.memory.dto;

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

    /** 章节大纲列表项：每个章节一行，含是否生成大纲、是否已写正文 */
    public record OutlineItemVO(Long chapterId, Integer chapterOrder, String chapterTitle,
                                Long wordCount, boolean hasContent, boolean hasOutline, String outline,
                                LocalDateTime updatedAt) {
    }

    /** 一键/批量生成大纲请求：指定章节则只生成这些，留空则生成全书未生成大纲的章节 */
    public record GenerateReq(List<Long> chapterIds) {
    }

    /** 手动新增 / 编辑单章大纲 */
    public record OutlineReq(
            @NotBlank(message = "大纲内容不能为空")
            @Size(max = 2000, message = "大纲最长 2000 字") String outline) {
    }
}
