package com.lingxi.world.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class WorldDtos {

    private WorldDtos() {
    }

    public record SaveReq(
            @NotBlank(message = "条目标题不能为空") @Size(max = 80, message = "标题最长 80 字") String title,
            @Size(max = 10000, message = "内容过长") String content) {
    }

    public record VO(Long id, Long novelId, String title, String content, LocalDateTime createdAt) {
    }
}
