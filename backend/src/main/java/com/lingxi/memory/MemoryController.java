package com.lingxi.memory;

import com.lingxi.common.ApiResponse;
import com.lingxi.memory.dto.MemoryDtos.GenerateReq;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineItemVO;
import com.lingxi.memory.dto.MemoryDtos.OutlineReq;
import com.lingxi.memory.dto.MemoryDtos.StoryBrainReq;
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

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
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

    /** 一键 / 批量生成大纲（body 可指定 chapterIds，留空则生成全书未生成大纲的章节） */
    @PostMapping("/api/novels/{novelId}/outlines/generate")
    public ApiResponse<List<OutlineItemVO>> generate(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                     @RequestBody(required = false) GenerateReq req) {
        List<Long> ids = req == null ? null : req.chapterIds();
        return ApiResponse.ok(memoryService.generate(novelId, user, ids));
    }

    /** 手动新增 / 编辑单章大纲 */
    @PutMapping("/api/novels/{novelId}/outlines/{chapterId}")
    public ApiResponse<Void> saveOutline(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                         @PathVariable Long chapterId, @Valid @RequestBody OutlineReq req) {
        memoryService.saveOutline(novelId, user, chapterId, req.outline());
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
