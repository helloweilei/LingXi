package com.lingxi.guide;

import com.lingxi.common.ApiResponse;
import com.lingxi.common.SseStreams;
import com.lingxi.guide.dto.GuideDtos;
import com.lingxi.user.User;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/guide")
public class IdeaGuideController {

    private final IdeaGuideService guideService;

    public IdeaGuideController(IdeaGuideService guideService) {
        this.guideService = guideService;
    }

    /** 历史引导会话列表（首页「历史方案」入口；按 id 倒序，limit 默认 20、上限 50） */
    @GetMapping("/intents")
    public ApiResponse<java.util.List<GuideDtos.IntentSummaryVO>> history(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(guideService.history(user, limit == null ? 20 : limit));
    }

    @PostMapping("/start")
    public ApiResponse<GuideDtos.GuideStartVO> start(@AuthenticationPrincipal User user,
                                                     @RequestBody(required = false) GuideDtos.StartReq req) {
        String seed = req == null ? null : req.seed();
        return ApiResponse.ok(guideService.start(user, seed));
    }

    @PostMapping("/{id}/answer")
    public ApiResponse<GuideDtos.AnswerVO> answer(@AuthenticationPrincipal User user,
                                                  @PathVariable Long id,
                                                  @RequestBody GuideDtos.AnswerReq req) {
        return ApiResponse.ok(guideService.answer(user, id, req.answer()));
    }

    @GetMapping("/{id}")
    public ApiResponse<GuideDtos.GuideStateVO> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(guideService.getState(user, id));
    }

    @PostMapping(value = "/{id}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generate(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return SseStreams.toSse(guideService.generate(user, id));
    }

    @PostMapping("/{id}/select")
    public ApiResponse<GuideDtos.SelectResult> select(@AuthenticationPrincipal User user,
                                                      @PathVariable Long id,
                                                      @RequestBody GuideDtos.SelectReq req) {
        return ApiResponse.ok(guideService.select(user, id, req.proposalIndex()));
    }

    /** 删除历史引导会话（仅删除该意图及其方案，不影响已生成的作品） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        guideService.delete(user, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset")
    public ApiResponse<Void> reset(@AuthenticationPrincipal User user, @PathVariable Long id) {
        guideService.reset(user, id);
        return ApiResponse.ok();
    }
}
