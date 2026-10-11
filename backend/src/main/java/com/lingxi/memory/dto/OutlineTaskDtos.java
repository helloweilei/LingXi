package com.lingxi.memory.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class OutlineTaskDtos {

    private OutlineTaskDtos() {
    }

    /** 发起批量生成大纲任务：chapterIds 留空表示全书未生成大纲的章节（不会创建章节，需已有章节） */
    public record StartReq(List<Long> chapterIds) {
    }

    /** 任务视图（前端轮询进度用） */
    public record TaskVO(Long id, Long novelId, String status,
                         Integer total, Integer currentIndex, String currentTitle,
                         Integer succeeded, Integer failed,
                         String error, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}