package com.lingxi.chapter;

import com.lingxi.chapter.dto.ChapterDtos.BatchDeleteReq;
import com.lingxi.chapter.dto.ChapterDtos.MetaVO;
import com.lingxi.chapter.dto.ChapterDtos.SaveReq;
import com.lingxi.chapter.dto.ChapterDtos.VO;
import com.lingxi.chapter.dto.ChapterDtos.VersionVO;
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

    /** 批量软删除章节（一次校验、循环标记删除，正文与版本保留可恢复） */
    @PostMapping("/api/chapters/batch-delete")
    public ApiResponse<Void> deleteBatch(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody BatchDeleteReq req) {
        chapterService.deleteBatch(req.ids(), user);
        return ApiResponse.ok();
    }

    /** 恢复已软删除的章节 */
    @PostMapping("/api/chapters/{id}/restore")
    public ApiResponse<Void> restore(@AuthenticationPrincipal User user, @PathVariable Long id) {
        chapterService.restore(id, user);
        return ApiResponse.ok();
    }

    /** 章节版本历史（含快照正文，供前端预览后回滚） */
    @GetMapping("/api/chapters/{id}/versions")
    public ApiResponse<List<VersionVO>> versions(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(chapterService.versions(id, user));
    }

    /** 删除指定版本快照（不影响章节当前内容） */
    @DeleteMapping("/api/chapters/{id}/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(@AuthenticationPrincipal User user, @PathVariable Long id,
                                          @PathVariable Long versionId) {
        chapterService.deleteVersion(id, versionId, user);
        return ApiResponse.ok();
    }

    /** 用指定快照恢复章节内容（恢复前自动留档当前状态） */
    @PostMapping("/api/chapters/{id}/versions/{versionId}/restore")
    public ApiResponse<VO> restoreVersion(@AuthenticationPrincipal User user, @PathVariable Long id,
                                          @PathVariable Long versionId) {
        return ApiResponse.ok(chapterService.restoreVersion(id, versionId, user));
    }
}
