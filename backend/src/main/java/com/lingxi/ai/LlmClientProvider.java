package com.lingxi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.config.AiProperties;
import com.lingxi.user.User;
import org.springframework.stereotype.Component;

/**
 * 按用户解析生效的 LLM 客户端：
 * 1. 用户已配置 openai 且填写了 API Key → 按用户配置实例化（DeepSeek 等 OpenAI 兼容服务）；
 * 2. 否则回退全局配置（application.yml 的 lingxi.ai.provider=openai 时走全局真实模型）；
 * 3. 两者皆无 → 内置示例引擎（mock）。
 */
@Component
public class LlmClientProvider {

    private final AiConfigRepository aiConfigRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final LlmClient globalClient;

    public LlmClientProvider(AiConfigRepository aiConfigRepository, AiProperties aiProperties,
                             ObjectMapper objectMapper, LlmClient globalClient) {
        this.aiConfigRepository = aiConfigRepository;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.globalClient = globalClient;
    }

    /** 当前用户的生效客户端（每个请求按需解析，用户改配置立即生效） */
    public LlmClient forUser(User user) {
        return aiConfigRepository.findByUserId(user.getId())
                .filter(c -> "openai".equals(c.getProvider()) && !c.getApiKey().isBlank())
                .<LlmClient>map(c -> new OpenAiCompatClient(c.getBaseUrl(), c.getApiKey(), c.getModel(),
                        aiProperties.getTemperature(), aiProperties.getReadTimeoutSeconds(),
                        aiProperties.getConnectTimeoutSeconds(), objectMapper))
                .orElse(globalClient);
    }

    /** 当前用户生效的 provider 名（status 接口展示用） */
    public String effectiveProvider(User user) {
        return aiConfigRepository.findByUserId(user.getId())
                .filter(c -> "openai".equals(c.getProvider()) && !c.getApiKey().isBlank())
                .map(c -> "openai")
                .orElseGet(() -> globalClient.name());
    }

    /** 当前用户生效的模型名（status 接口展示用） */
    public String effectiveModel(User user) {
        return aiConfigRepository.findByUserId(user.getId())
                .filter(c -> "openai".equals(c.getProvider()) && !c.getApiKey().isBlank())
                .map(AiConfig::getModel)
                .orElseGet(aiProperties::getModel);
    }
}
