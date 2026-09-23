package com.lingxi.world;

import com.lingxi.common.ApiResponse;
import com.lingxi.user.User;
import com.lingxi.world.dto.WorldDtos.SaveReq;
import com.lingxi.world.dto.WorldDtos.VO;
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
public class WorldSettingController {

    private final WorldSettingService worldSettingService;

    public WorldSettingController(WorldSettingService worldSettingService) {
        this.worldSettingService = worldSettingService;
    }

    @GetMapping("/api/novels/{novelId}/worldsettings")
    public ApiResponse<List<VO>> list(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(worldSettingService.list(novelId, user));
    }

    @PostMapping("/api/novels/{novelId}/worldsettings")
    public ApiResponse<VO> create(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                  @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(worldSettingService.create(novelId, user, req));
    }

    @PutMapping("/api/worldsettings/{id}")
    public ApiResponse<VO> update(@AuthenticationPrincipal User user, @PathVariable Long id,
                                  @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(worldSettingService.update(id, user, req));
    }

    @DeleteMapping("/api/worldsettings/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        worldSettingService.delete(id, user);
        return ApiResponse.ok();
    }
}
