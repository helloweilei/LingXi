package com.lingxi.memory;

import com.lingxi.common.ApiResponse;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowReq;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowStatusReq;
import com.lingxi.memory.dto.MemoryDtos.ForeshadowingVO;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 伏笔账本接口：跨章节悬念的结构化登记。
 * OPEN 条目会作为【未回收伏笔清单】注入大纲与正文生成 prompt（见 ContextBuilder）。
 */
@RestController
@RequestMapping("/api/novels/{novelId}/foreshadowings")
public class ForeshadowingController {

    private final ForeshadowingService service;

    public ForeshadowingController(ForeshadowingService service) {
        this.service = service;
    }

    /** 全书伏笔列表（含各状态，前端过滤展示） */
    @GetMapping
    public ApiResponse<List<ForeshadowingVO>> list(@AuthenticationPrincipal User user,
                                                   @PathVariable Long novelId) {
        return ApiResponse.ok(service.list(novelId, user));
    }

    /** 登记一条新伏笔（默认 OPEN） */
    @PostMapping
    public ApiResponse<ForeshadowingVO> create(@AuthenticationPrincipal User user,
                                               @PathVariable Long novelId,
                                               @Valid @RequestBody ForeshadowReq req) {
        return ApiResponse.ok(service.create(novelId, user, req));
    }

    /** 编辑伏笔（字段传 null 表示不改） */
    @PutMapping("/{foreshadowId}")
    public ApiResponse<Void> update(@AuthenticationPrincipal User user,
                                    @PathVariable Long novelId,
                                    @PathVariable Long foreshadowId,
                                    @RequestBody ForeshadowReq req) {
        service.update(novelId, foreshadowId, user, req);
        return ApiResponse.ok();
    }

    /** 状态流转：OPEN / RESOLVED / ABANDONED（RESOLVED 建议带实际回收章序） */
    @PutMapping("/{foreshadowId}/status")
    public ApiResponse<Void> changeStatus(@AuthenticationPrincipal User user,
                                          @PathVariable Long novelId,
                                          @PathVariable Long foreshadowId,
                                          @RequestBody ForeshadowStatusReq req) {
        service.changeStatus(novelId, foreshadowId, user, req);
        return ApiResponse.ok();
    }

    /** 删除一条伏笔 */
    @DeleteMapping("/{foreshadowId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal User user,
                                    @PathVariable Long novelId,
                                    @PathVariable Long foreshadowId) {
        service.remove(novelId, foreshadowId, user);
        return ApiResponse.ok();
    }
}
