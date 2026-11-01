package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ChatReq;
import com.lingxi.ai.dto.AiDtos.TextReq;
import com.lingxi.common.ApiResponse;
import com.lingxi.common.SseStreams;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowSuggestVO;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    /**
     * 生成整章正文的请求：
     * - totalChapters：可选，全书总章节数，用于判断本章是否为终章（决定是否收束全书）；
     * - authorNote：可选，「作者有话说」——作者对本章的干预要求（重点情节、人物处理、禁区等），
     *   非空时以最高优先级注入正文生成 prompt。
     */
    record WriteReq(Integer totalChapters, String authorNote) {
    }

    /** 已生成的视频提示词（列表展示 + 直接查看） */
    record VideoPromptVO(Long id, Long chapterId, String mode, String content, LocalDateTime updatedAt) {
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

    /** 文生视频提示词请求：chapterId 非空 = 单章分镜；为空 = 全书宣传片 */
    record VideoPromptReq(Long chapterId) {
    }

    /**
     * 独立工作台「粘贴外部文本」生成视频提示词的请求：
     * - title：书名（可选，用于宣传片末镜头字幕）；
     * - content：用户粘贴的章节/情节文本（至少 50 字）；
     * - storyboard：true=单章分镜；false=宣传片。结果不落库，由用户自行复制。
     */
    record AdhocVideoPromptReq(String title, String content, Boolean storyboard) {
    }

    /**
     * 生成文生视频提示词（流式）：只产出可直接粘贴到即梦/可灵/Runway 等平台的分镜提示词，
     * 不调用视频生成模型。产出按「风格锚点 + 角色锚点 + 逐镜头」结构组织，保证跨镜头一致性。
     */
    @PostMapping(value = "/novels/{novelId}/video-prompt", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> videoPrompt(@AuthenticationPrincipal User user,
                                                     @PathVariable Long novelId,
                                                     @RequestBody(required = false) VideoPromptReq req) {
        Long chapterId = req != null ? req.chapterId() : null;
        return SseStreams.toSse(aiService.videoPrompt(novelId, chapterId, user));
    }

    /**
     * 独立工作台「粘贴外部文本」生成视频提示词：不依赖数据库作品，结果不落库。
     * 与按作品生成共用同一输出协议（风格/角色锚点 + 逐镜头）。
     */
    @PostMapping(value = "/video-prompt/adhoc", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> videoPromptAdhoc(@AuthenticationPrincipal User user,
                                                          @RequestBody AdhocVideoPromptReq req) {
        boolean storyboard = req == null || req.storyboard() == null || req.storyboard();
        String content = req == null ? null : req.content();
        String title = req == null ? null : req.title();
        return SseStreams.toSse(aiService.videoPromptAdhoc(user, title, content, storyboard));
    }

    /** 已生成的视频提示词列表（章节分镜 + 宣传片，含全部内容，前端直接查看） */
    @GetMapping("/novels/{novelId}/video-prompts")
    public ApiResponse<List<VideoPromptVO>> videoPrompts(@AuthenticationPrincipal User user,
                                                         @PathVariable Long novelId) {
        List<VideoPromptVO> list = aiService.listVideoPrompts(novelId, user).stream()
                .map(p -> new VideoPromptVO(p.getId(), p.getChapterId(), p.getMode(),
                        p.getContent(), p.getUpdatedAt()))
                .toList();
        return ApiResponse.ok(list);
    }

    /** 编辑作品时依据全书大纲一键生成简介（流式；不落库，由用户在表单确认后保存） */
    @PostMapping(value = "/novels/{novelId}/description", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> description(@AuthenticationPrincipal User user,
                                                     @PathVariable Long novelId) {
        return SseStreams.toSse(aiService.descriptionFromOutline(novelId, user));
    }

    /** 删除一条已生成的视频提示词 */
    @DeleteMapping("/novels/{novelId}/video-prompts/{videoPromptId}")
    public ApiResponse<Void> deleteVideoPrompt(@AuthenticationPrincipal User user,
                                               @PathVariable Long novelId,
                                               @PathVariable Long videoPromptId) {
        aiService.deleteVideoPrompt(novelId, videoPromptId, user);
        return ApiResponse.ok();
    }

    /**
     * 半自动伏笔记账：对刚写完的一章正文，让 AI 对照账本提炼「新埋伏笔 / 已回收伏笔」建议。
     * 只返回建议**不落库**——作者在前端确认后才通过账本接口写入；失败静默返回空建议。
     */
    @PostMapping("/novels/{novelId}/chapters/{chapterId}/foreshadow-suggest")
    public ApiResponse<ForeshadowSuggestVO> foreshadowSuggest(@AuthenticationPrincipal User user,
                                                              @PathVariable Long novelId,
                                                              @PathVariable Long chapterId) {
        return ApiResponse.ok(aiService.suggestForeshadowing(novelId, chapterId, user));
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
        String authorNote = req != null ? req.authorNote() : null;
        return SseStreams.toSse(aiService.writeChapterAndSave(novelId, outlineId, user, total, false, authorNote));
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
        String authorNote = req != null ? req.authorNote() : null;
        return SseStreams.toSse(aiService.writeChapterAndSave(novelId, outlineId, user, total, true, authorNote));
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
