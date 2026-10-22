package com.lingxi.speech.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class SpeechDtos {

    private SpeechDtos() {
    }

    /** 语音配置（不回显完整密钥，仅给掩码） */
    public record ConfigVO(String provider, String appId, String apiKeyMask, boolean hasKey,
                           boolean hasSecret, LocalDateTime updatedAt) {
    }

    /** 保存语音配置；apiKey / apiSecret 为空表示沿用旧值 */
    public record ConfigReq(
            @Pattern(regexp = "browser|xfyun|aliyun", message = "provider 仅支持 browser / xfyun / aliyun") String provider,
            @Size(max = 100, message = "APPID 最长 100 字") String appId,
            @Size(max = 255, message = "API Key 最长 255 字") String apiKey,
            @Size(max = 255, message = "API Secret 最长 255 字") String apiSecret) {
    }

    public record RecognizeVO(String text) {
    }
}
