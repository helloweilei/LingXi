package com.lingxi.assistant;

import com.lingxi.common.SseStreams;
import com.lingxi.user.User;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 创作助手：自由聊天收集创作诉求 + 一键生成小说大纲。
 * 两个接口都是无状态 SSE——前端每次携带全量对话，服务端不落库。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /** 开场引导问题响应：openers 为最终可展示、可点选发送的问题列表 */
    public record OpenersRes(List<String> openers) {
    }

    /**
     * 开场引导问题（初次进入时展示）：AI 依据当前热门题材生成，
     * 模型不可用 / 超时 / 输出不可解析时返回静态兜底模板——本接口永不抛错。
     */
    @GetMapping("/openers")
    public OpenersRes openers(@AuthenticationPrincipal User user) {
        return new OpenersRes(assistantService.hotOpeners(user));
    }

    /** 对话请求：messages 为按时间先后的全量对话（role: user / assistant） */
    public record ChatReq(List<AssistantService.ChatMsg> messages) {
    }

    /** 创作诉求收集聊天（流式）：助手在对话中逐步补齐题材/创意/主角/基调/篇幅等诉求 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@AuthenticationPrincipal User user,
                                              @RequestBody ChatReq req) {
        return SseStreams.toSse(assistantService.chat(user, req == null ? null : req.messages()));
    }

    /** 根据收集到的诉求生成小说大纲（流式）：诉求齐备后由前端触发 */
    @PostMapping(value = "/outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> outline(@AuthenticationPrincipal User user,
                                                 @RequestBody ChatReq req) {
        return SseStreams.toSse(assistantService.outline(user, req == null ? null : req.messages()));
    }
}
