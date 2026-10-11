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

    /** 生成整章正文的请求：可选传入全书总章节数，用于判断本章是否为终章（决定是否收束全书） */
    record WriteReq(Integer totalChapters) {
    }

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

    /** 生成章节大纲：流式输出，完成后自动写入该章大纲（chapter_outlines），用于创作前规划 */
    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateChapterOutline(@AuthenticationPrincipal User user,
                                                               @PathVariable Long novelId,
                                                               @PathVariable Long chapterId) {
        return toSse(aiService.generateChapterOutline(novelId, chapterId, user));
    }

    /** 生成封面提示词请求：authorName 可选，作者名会原样写入提示词并禁止 AI 改写 */
    record CoverReq(String authorName) {
    }

    /** 为未命名章节生成一个有信息量的章节名（流式，输出可直接用作标题） */
    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/title", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chapterTitle(@AuthenticationPrincipal User user,
                                                       @PathVariable Long novelId,
                                                       @PathVariable Long chapterId) {
        return toSse(aiService.chapterTitle(novelId, chapterId, user));
    }

    @PostMapping(value = "/novels/{novelId}/cover-prompt", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> coverPrompt(@AuthenticationPrincipal User user,
                                                      @PathVariable Long novelId,
                                                      @RequestBody(required = false) CoverReq req) {
        String authorName = req != null ? req.authorName() : null;
        return toSse(aiService.coverPrompt(novelId, authorName, user));
    }

    /**
     * 根据章节大纲生成整章正文：流式输出，并在完成后整章覆盖写入（旧内容由 ChapterService 自动留版本快照）。
     * 每章目标字数由后端按“总目标字数 / 章节数”动态计算，前端无需也不应传入字数。
     * 章节已存在正文时，前端应先弹确认提示覆盖风险再调用。
     */
    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> writeChapter(@AuthenticationPrincipal User user,
                                                      @PathVariable Long novelId,
                                                      @PathVariable Long chapterId,
                                                      @RequestBody(required = false) WriteReq req) {
        Integer total = req != null ? req.totalChapters() : null;
        return toSse(aiService.writeChapterAndSave(novelId, chapterId, user, total));
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
