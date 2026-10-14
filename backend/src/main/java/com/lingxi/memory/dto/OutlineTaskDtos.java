package com.lingxi.memory.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class OutlineTaskDtos {

    private OutlineTaskDtos() {
    }

    /**
     * 发起批量生成大纲任务：outlineIds 留空表示全书大纲内容为空的规划行。
     * 仅对已有规划行生成内容，不会创建规划或章节。
     */
    public record StartReq(List<Long> outlineIds) {
    }

    /** 任务视图（前端轮询进度用） */
    public record TaskVO(Long id, Long novelId, String status,
                         Integer total, Integer currentIndex, String currentTitle,
                         Integer succeeded, Integer failed,
                         String error, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}