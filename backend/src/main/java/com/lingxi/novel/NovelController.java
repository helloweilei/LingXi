package com.lingxi.novel;

import com.lingxi.common.ApiResponse;
import com.lingxi.novel.dto.NovelDtos.CreateReq;
import com.lingxi.novel.dto.NovelDtos.DetailVO;
import com.lingxi.novel.dto.NovelDtos.UpdateReq;
import com.lingxi.novel.dto.NovelDtos.VO;
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

@RestController
@RequestMapping("/api/novels")
public class NovelController {

    private final NovelService novelService;

    public NovelController(NovelService novelService) {
        this.novelService = novelService;
    }

    @GetMapping
    public ApiResponse<List<VO>> list(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(novelService.list(user));
    }

    @PostMapping
    public ApiResponse<VO> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateReq req) {
        return ApiResponse.ok(novelService.create(user, req));
    }

    @GetMapping("/{id}")
    public ApiResponse<DetailVO> detail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(novelService.detail(id, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<DetailVO> update(@AuthenticationPrincipal User user, @PathVariable Long id,
                                        @Valid @RequestBody UpdateReq req) {
        return ApiResponse.ok(novelService.update(id, user, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        novelService.delete(id, user);
        return ApiResponse.ok();
    }
}
