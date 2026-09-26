package com.lingxi.task;

import com.lingxi.common.ApiResponse;
import com.lingxi.task.dto.WritingTaskDtos.AutoWriteReq;
import com.lingxi.task.dto.WritingTaskDtos.TaskVO;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WritingTaskController {

    private final AutoWriteService autoWriteService;

    public WritingTaskController(AutoWriteService autoWriteService) {
        this.autoWriteService = autoWriteService;
    }

    /** 发起自动创作任务 */
    @PostMapping("/novels/{novelId}/auto")
    public ApiResponse<TaskVO> start(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                     @Valid @RequestBody AutoWriteReq req) {
        return ApiResponse.ok(autoWriteService.start(novelId, user, req));
    }

    /** 某本书的任务列表（近 20 条） */
    @GetMapping("/novels/{novelId}/auto/tasks")
    public ApiResponse<List<TaskVO>> list(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(autoWriteService.list(novelId, user));
    }

    /** 任务详情（前端轮询进度） */
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskVO> get(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        return ApiResponse.ok(autoWriteService.get(taskId, user));
    }

    /** 批准继续（CONFIRM 模式） */
    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<TaskVO> approve(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        return ApiResponse.ok(autoWriteService.approve(taskId, user));
    }

    /** 取消任务 */
    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<TaskVO> cancel(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        return ApiResponse.ok(autoWriteService.cancel(taskId, user));
    }

    /** 删除任务并移除其生成的内容（仅终态任务；章节软删可恢复，生成的大纲会清空） */
    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal User user, @PathVariable Long taskId) {
        autoWriteService.remove(taskId, user);
        return ApiResponse.ok();
    }
}
