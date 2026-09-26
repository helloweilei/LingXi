package com.lingxi.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
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

    /** 模型配置（不回显完整 API Key，仅给掩码） */
    public record ConfigVO(String provider, String baseUrl, String model,
                           String apiKeyMask, boolean hasKey, LocalDateTime updatedAt) {
    }

    /** 保存用户级模型配置；apiKey 为空串表示不修改现有 Key */
    public record ConfigReq(
            @Pattern(regexp = "mock|openai", message = "provider 仅支持 mock 或 openai") String provider,
            @Size(max = 255, message = "接口地址最长 255 字") String baseUrl,
            @Size(max = 255, message = "API Key 最长 255 字") String apiKey,
            @Size(max = 100, message = "模型名最长 100 字") String model) {
    }

    /** 连通性测试结果 */
    public record TestVO(boolean ok, long latencyMs, String message) {
    }
}
