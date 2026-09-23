package com.lingxi.chapter;

import com.lingxi.chapter.dto.ChapterDtos.MetaVO;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.chapter.dto.ChapterDtos.VO;
import com.lingxi.common.ApiResponse;
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
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/api/novels/{novelId}/chapters")
    public ApiResponse<List<MetaVO>> list(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(chapterService.list(novelId, user));
    }

    @PostMapping("/api/novels/{novelId}/chapters")
    public ApiResponse<VO> create(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                  @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(chapterService.create(novelId, user, req));
    }

    @GetMapping("/api/chapters/{id}")
    public ApiResponse<VO> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(chapterService.get(id, user));
    }

    @PutMapping("/api/chapters/{id}")
    public ApiResponse<VO> save(@AuthenticationPrincipal User user, @PathVariable Long id,
                                @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(chapterService.save(id, user, req));
    }

    @DeleteMapping("/api/chapters/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        chapterService.delete(id, user);
        return ApiResponse.ok();
    }
}
