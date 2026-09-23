package com.lingxi.character;

import com.lingxi.character.dto.CharacterDtos.SaveReq;
import com.lingxi.character.dto.CharacterDtos.VO;
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
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping("/api/novels/{novelId}/characters")
    public ApiResponse<List<VO>> list(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(characterService.list(novelId, user));
    }

    @PostMapping("/api/novels/{novelId}/characters")
    public ApiResponse<VO> create(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                  @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(characterService.create(novelId, user, req));
    }

    @PutMapping("/api/characters/{id}")
    public ApiResponse<VO> update(@AuthenticationPrincipal User user, @PathVariable Long id,
                                  @Valid @RequestBody SaveReq req) {
        return ApiResponse.ok(characterService.update(id, user, req));
    }

    @DeleteMapping("/api/characters/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        characterService.delete(id, user);
        return ApiResponse.ok();
    }
}
