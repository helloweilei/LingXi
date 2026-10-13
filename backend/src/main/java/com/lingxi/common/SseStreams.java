package com.lingxi.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 把模型增量文本流转成 SSE：每段为 delta，结束后发 done，出错发 error。
 */
public final class SseStreams {

    private static final Logger log = LoggerFactory.getLogger(SseStreams.class);

    private SseStreams() {
    }

    public static Flux<ServerSentEvent<String>> toSse(Flux<String> deltas) {
        return deltas.map(t -> ServerSentEvent.<String>builder(t).event("delta").build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder("").event("done").build()))
                .onErrorResume(e -> {
                    String msg = e instanceof BizException be ? be.getMessage() : "AI 服务暂时不可用，请稍后重试";
                    log.error("AI stream error: {}", e.toString());
                    return Mono.just(ServerSentEvent.<String>builder(msg).event("error").build());
                });
    }
}
