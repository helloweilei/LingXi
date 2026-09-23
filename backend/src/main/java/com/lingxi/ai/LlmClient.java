package com.lingxi.ai;

import reactor.core.publisher.Flux;

public interface LlmClient {

    /** 逐段返回增量文本，流式输出 */
    Flux<String> stream(AiTaskType type, AiContext context, String systemPrompt, String userPrompt);

    /** 供应商标识：mock / openai */
    String name();
}
