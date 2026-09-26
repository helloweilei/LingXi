package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ChatReq;
import com.lingxi.ai.dto.AiDtos.TextReq;
import com.lingxi.common.ApiResponse;
import com.lingxi.common.BizException;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;
    private final LlmClientProvider llmClientProvider;

    public AiController(AiService aiService, LlmClientProvider llmClientProvider) {
        this.aiService = aiService;
        this.llmClientProvider = llmClientProvider;
    }

    /** 返回当前用户生效的模型状态（用户级配置优先） */
    @GetMapping("/status")
    public ApiResponse<Map<String, String>> status(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(Map.of(
                "provider", llmClientProvider.effectiveProvider(user),
                "model", llmClientProvider.effectiveModel(user)));
    }

    @PostMapping(value = "/novels/{novelId}/outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> outline(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return toSse(aiService.outline(novelId, user));
    }

    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> continueChapter(@AuthenticationPrincipal User user,
                                                         @PathVariable Long novelId,
                                                         @PathVariable Long chapterId) {
        return toSse(aiService.continueChapter(novelId, chapterId, user));
    }

    @PostMapping(value = "/novels/{novelId}/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> polish(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                @Valid @RequestBody TextReq req) {
        return toSse(aiService.polish(novelId, req.text(), user));
    }

    @PostMapping(value = "/novels/{novelId}/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> expand(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                @Valid @RequestBody TextReq req) {
        return toSse(aiService.expand(novelId, req.text(), user));
    }

    @PostMapping(value = "/novels/{novelId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                              @Valid @RequestBody ChatReq req) {
        return toSse(aiService.chat(novelId, req, user));
    }

    /** 生成章节摘要：流式输出，完成后自动写入该章的记忆（chapter_summaries） */
    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> summarize(@AuthenticationPrincipal User user,
                                                   @PathVariable Long novelId,
                                                   @PathVariable Long chapterId) {
        return toSse(aiService.summarize(novelId, chapterId, user));
    }

    private Flux<ServerSentEvent<String>> toSse(Flux<String> deltas) {
        return deltas.map(t -> ServerSentEvent.<String>builder(t).event("delta").build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder("").event("done").build()))
                .onErrorResume(e -> {
                    String msg = e instanceof BizException be ? be.getMessage() : "AI 服务暂时不可用，请稍后重试";
                    log.error("AI stream error: {}", e.toString());
                    return Mono.just(ServerSentEvent.<String>builder(msg).event("error").build());
                });
    }
}
