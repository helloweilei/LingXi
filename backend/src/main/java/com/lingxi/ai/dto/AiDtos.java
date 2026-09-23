package com.lingxi.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    public record TextReq(
            @NotBlank(message = "文本不能为空") @Size(max = 8000, message = "文本最长 8000 字") String text) {
    }

    public record ChatMsg(String role, String content) {
    }

    public record ChatReq(
            @NotBlank(message = "问题不能为空") @Size(max = 2000, message = "问题最长 2000 字") String message,
            List<ChatMsg> history) {
    }
}
