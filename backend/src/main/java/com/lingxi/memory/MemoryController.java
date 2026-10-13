package com.lingxi.memory;

import com.lingxi.common.ApiResponse;
import com.lingxi.memory.dto.MemoryDtos.ChapterWordsReq;
import com.lingxi.memory.dto.MemoryDtos.GenerateReq;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineItemVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineReq;
import com.lingxi.memory.dto.MemoryDtos.StoryBrainReq;
import com.lingxi.memory.dto.OutlineTaskDtos.StartReq;
import com.lingxi.memory.dto.OutlineTaskDtos.TaskVO;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MemoryController {

    private final MemoryService memoryService;
    private final OutlineTaskService outlineTaskService;

    public MemoryController(MemoryService memoryService, OutlineTaskService outlineTaskService) {
        this.memoryService = memoryService;
        this.outlineTaskService = outlineTaskService;
    }

    /** 全书记忆视图：章节大纲（按章序）+ 故事脑 */
    @GetMapping("/api/novels/{novelId}/memory")
    public ApiResponse<MemoryVO> view(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(memoryService.view(novelId, user));
    }

    /** 手动覆盖更新故事脑（P2 自动流水线接入后由 AI 自动更新） */
    @PutMapping("/api/novels/{novelId}/memory/story-brain")
    public ApiResponse<StoryMemory> saveStoryBrain(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                   @Valid @RequestBody StoryBrainReq req) {
        return ApiResponse.ok(memoryService.saveStoryBrain(novelId, user, req.storyBrain()));
    }

    /** 章节大纲列表：每个章节一行，标注是否已生成大纲 */
    @GetMapping("/api/novels/{novelId}/outlines")
    public ApiResponse<List<OutlineItemVO>> outlines(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(memoryService.outlines(novelId, user));
    }

    /** 一键 / 批量生成大纲（body 可指定 chapterIds，留空则生成全书未生成大纲的章节；不会创建章节） */
    @PostMapping("/api/novels/{novelId}/outlines/generate")
    public ApiResponse<List<OutlineItemVO>> generate(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                     @RequestBody(required = false) GenerateReq req) {
        List<Long> ids = req == null ? null : req.chapterIds();
        return ApiResponse.ok(memoryService.generate(novelId, user, ids));
    }

    /**
     * 提交大纲批量生成任务（推荐）：后台线程池逐章生成，避免同步串行调用 LLM 触发 HTTP 超时。
     * 仅对已存在的章节生成大纲，不会创建章节。
     */
    @PostMapping("/api/novels/{novelId}/outline-tasks")
    public ApiResponse<TaskVO> startOutlineTask(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                @RequestBody(required = false) StartReq req) {
        return ApiResponse.ok(outlineTaskService.start(novelId, user, req));
    }

    /** 某本书的大纲生成任务列表（近 20 条） */
    @GetMapping("/api/novels/{novelId}/outline-tasks")
    public ApiResponse<List<TaskVO>> listOutlineTasks(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(outlineTaskService.list(novelId, user));
    }

    /** 大纲任务详情（前端轮询进度） */
    @GetMapping("/api/outline-tasks/{taskId}")
    public ApiResponse<TaskVO> getOutlineTask(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        return ApiResponse.ok(outlineTaskService.get(taskId, user));
    }

    /** 取消大纲生成任务 */
    @PostMapping("/api/outline-tasks/{taskId}/cancel")
    public ApiResponse<TaskVO> cancelOutlineTask(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        return ApiResponse.ok(outlineTaskService.cancel(taskId, user));
    }

    /** 删除已结束的大纲任务记录 */
    @DeleteMapping("/api/outline-tasks/{taskId}")
    public ApiResponse<Void> removeOutlineTask(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        outlineTaskService.remove(taskId, user);
        return ApiResponse.ok();
    }

    /** 手动新增 / 编辑单章大纲（targetWords 可选，null 表示沿用全书均摊值） */
    @PutMapping("/api/novels/{novelId}/outlines/{chapterId}")
    public ApiResponse<Void> saveOutline(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                         @PathVariable Long chapterId, @Valid @RequestBody OutlineReq req) {
        memoryService.saveOutline(novelId, user, chapterId, req.outline(), req.targetWords());
        return ApiResponse.ok();
    }

    /** 单独设置/恢复某章目标字数（不改动大纲内容；words 传 0 或不传即恢复默认均摊） */
    @PutMapping("/api/novels/{novelId}/outlines/{chapterId}/words")
    public ApiResponse<Void> saveChapterWords(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                              @PathVariable Long chapterId,
                                              @RequestBody ChapterWordsReq req) {
        memoryService.saveChapterWords(novelId, user, chapterId, req == null ? null : req.words());
        return ApiResponse.ok();
    }

    /** 删除单章大纲 */
    @DeleteMapping("/api/novels/{novelId}/outlines/{chapterId}")
    public ApiResponse<Void> deleteOutline(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                           @PathVariable Long chapterId) {
        memoryService.deleteOutline(novelId, user, chapterId);
        return ApiResponse.ok();
    }
}
