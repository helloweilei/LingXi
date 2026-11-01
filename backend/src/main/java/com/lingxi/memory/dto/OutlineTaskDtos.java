package com.lingxi.memory.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class OutlineTaskDtos {

    private OutlineTaskDtos() {
    }

    /**
     * 发起批量生成大纲任务：outlineIds 留空表示全书大纲内容为空的规划行。
     * chapters 仅在全书还没有任何规划行时有意义：先按数量补建空规划行，再逐条生成内容，
     * 使用户可以「一键从零规划全书」——不必先手动新增规划。
     * authorNote：「作者有话说」——对每章大纲规划的干预要求（可选，对整批生效）。
     */
    public record StartReq(List<Long> outlineIds, Integer chapters, String authorNote) {
        public StartReq(List<Long> outlineIds) {
            this(outlineIds, null, null);
        }

        public StartReq(List<Long> outlineIds, Integer chapters) {
            this(outlineIds, chapters, null);
        }
    }

    /** 任务视图（前端轮询进度用） */
    public record TaskVO(Long id, Long novelId, String status,
                         Integer total, Integer currentIndex, String currentTitle,
                         Integer succeeded, Integer failed,
                         String error, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}