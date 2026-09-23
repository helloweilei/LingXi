package com.lingxi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public LlmClient llmClient(AiProperties props, ObjectMapper objectMapper) {
        return props.isRealLlm() ? new OpenAiCompatClient(props, objectMapper) : new MockLlmClient();
    }
}
