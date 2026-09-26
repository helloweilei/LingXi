package com.lingxi.memory;

import com.lingxi.common.ApiResponse;
import com.lingxi.memory.dto.MemoryDtos.MemoryVO;
import com.lingxi.memory.dto.MemoryDtos.StoryBrainReq;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /** 全书记忆视图：章节摘要（按章序）+ 故事脑 */
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
}
