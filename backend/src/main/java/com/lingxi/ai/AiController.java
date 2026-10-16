package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ChatReq;
import com.lingxi.ai.dto.AiDtos.TextReq;
import com.lingxi.common.ApiResponse;
import com.lingxi.common.SseStreams;
import com.lingxi.user.User;
import jakarta.validation.Valid;
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

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    /** 生成整章正文的请求：可选传入全书总章节数，用于判断本章是否为终章（决定是否收束全书） */
    record WriteReq(Integer totalChapters) {
    }

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
        return SseStreams.toSse(aiService.outline(novelId, user));
    }

    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> continueChapter(@AuthenticationPrincipal User user,
                                                         @PathVariable Long novelId,
                                                         @PathVariable Long chapterId) {
        return SseStreams.toSse(aiService.continueChapter(novelId, chapterId, user));
    }

    @PostMapping(value = "/novels/{novelId}/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> polish(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                @Valid @RequestBody TextReq req) {
        return SseStreams.toSse(aiService.polish(novelId, req.text(), user));
    }

    /** 去AI化：不改变语义，改写AI腔句子，让行文更自然、更有"人味" */
    @PostMapping(value = "/novels/{novelId}/deai", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> deai(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                              @Valid @RequestBody TextReq req) {
        return SseStreams.toSse(aiService.deAi(novelId, req.text(), user));
    }

    @PostMapping(value = "/novels/{novelId}/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> expand(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                @Valid @RequestBody TextReq req) {
        return SseStreams.toSse(aiService.expand(novelId, req.text(), user));
    }

    @PostMapping(value = "/novels/{novelId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                              @Valid @RequestBody ChatReq req) {
        return SseStreams.toSse(aiService.chat(novelId, req, user));
    }

    /**
     * 生成一条大纲规划（流式）：完成后自动写回该规划行的章节名与大纲内容。
     * 大纲先于章节存在，不需要已有章节。
     */
    @PostMapping(value = "/novels/{novelId}/outlines/{outlineId}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateChapterOutline(@AuthenticationPrincipal User user,
                                                               @PathVariable Long novelId,
                                                               @PathVariable Long outlineId) {
        return SseStreams.toSse(aiService.generateChapterOutline(novelId, outlineId, user));
    }

    /** 生成封面提示词请求：authorName 可选，作者名会原样写入提示词并禁止 AI 改写 */
    record CoverReq(String authorName) {
    }

    /** 为未命名章节生成一个有信息量的章节名（流式，输出可直接用作标题） */
    @PostMapping(value = "/novels/{novelId}/chapters/{chapterId}/title", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chapterTitle(@AuthenticationPrincipal User user,
                                                       @PathVariable Long novelId,
                                                       @PathVariable Long chapterId) {
        return SseStreams.toSse(aiService.chapterTitle(novelId, chapterId, user));
    }

    @PostMapping(value = "/novels/{novelId}/cover-prompt", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> coverPrompt(@AuthenticationPrincipal User user,
                                                      @PathVariable Long novelId,
                                                      @RequestBody(required = false) CoverReq req) {
        String authorName = req != null ? req.authorName() : null;
        return SseStreams.toSse(aiService.coverPrompt(novelId, authorName, user));
    }

    /**
     * 按大纲规划生成整章正文（**整章重写**）：流式输出，完成后覆盖写入（旧内容自动留版本快照，可回滚）。
     * 若该规划尚未生成过正文，会在此创建对应章节——**章节由大纲落地而来**。
     * 忽略本章已有正文，从上一章结尾重新写起——这是「生成正文」按钮的语义。
     */
    @PostMapping(value = "/novels/{novelId}/outlines/{outlineId}/write", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> writeChapter(@AuthenticationPrincipal User user,
                                                      @PathVariable Long novelId,
                                                      @PathVariable Long outlineId,
                                                      @RequestBody(required = false) WriteReq req) {
        Integer total = req != null ? req.totalChapters() : null;
        return SseStreams.toSse(aiService.writeChapterAndSave(novelId, outlineId, user, total, false));
    }

    /**
     * 从本章当前内容**接着写**（只增不重）：仅在已有正文时可用，结果追加到原文之后。
     * 这是与「生成正文」不同的独立能力，对应前端「从当前内容接着写」入口。
     */
    @PostMapping(value = "/novels/{novelId}/outlines/{outlineId}/write-continue",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> writeChapterContinue(@AuthenticationPrincipal User user,
                                                              @PathVariable Long novelId,
                                                              @PathVariable Long outlineId,
                                                              @RequestBody(required = false) WriteReq req) {
        Integer total = req != null ? req.totalChapters() : null;
        return SseStreams.toSse(aiService.writeChapterAndSave(novelId, outlineId, user, total, true));
    }

    /** 错别字校对请求：text 为待校对文本 */
    record ProofreadReq(String text) {
    }

    /**
     * 错别字/用词校对：流式输出 JSON 数组。
     * 每条形如 {"wrong":"错误片段","suggestion":"建议","type":"错别字|词语误用|语病|标点|称呼不一致","reason":"说明"}
     * wrong 为原文中逐字连续的片段，便于前端精确定位替换。
     */
    @PostMapping(value = "/proofread", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> proofread(@AuthenticationPrincipal User user,
                                                   @RequestBody ProofreadReq req) {
        return SseStreams.toSse(aiService.proofread(req == null ? "" : req.text(), user));
    }
}
