package com.lingxi.character;

import com.lingxi.character.dto.CharacterDtos.RelationReq;
import com.lingxi.character.dto.CharacterDtos.RelationVO;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/api/novels/{novelId}/character-relations")
    public ApiResponse<List<RelationVO>> listRelations(@AuthenticationPrincipal User user, @PathVariable Long novelId) {
        return ApiResponse.ok(characterService.listRelations(novelId, user));
    }

    @PostMapping("/api/novels/{novelId}/character-relations")
    public ApiResponse<RelationVO> createRelation(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                  @Valid @RequestBody RelationReq req) {
        return ApiResponse.ok(characterService.createRelation(novelId, user, req));
    }

    @PostMapping("/api/novels/{novelId}/character-relations/infer")
    public ApiResponse<List<RelationVO>> inferRelations(@AuthenticationPrincipal User user, @PathVariable Long novelId,
                                                        @RequestParam(defaultValue = "false") boolean replace) {
        return ApiResponse.ok(characterService.inferRelations(novelId, user, replace));
    }

    @PutMapping("/api/character-relations/{id}")
    public ApiResponse<RelationVO> updateRelation(@AuthenticationPrincipal User user, @PathVariable Long id,
                                                  @Valid @RequestBody RelationReq req) {
        return ApiResponse.ok(characterService.updateRelation(id, user, req));
    }

    @DeleteMapping("/api/character-relations/{id}")
    public ApiResponse<Void> deleteRelation(@AuthenticationPrincipal User user, @PathVariable Long id) {
        characterService.deleteRelation(id, user);
        return ApiResponse.ok();
    }
}
