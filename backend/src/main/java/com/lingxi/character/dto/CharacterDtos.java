package com.lingxi.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CharacterDtos {

    private CharacterDtos() {
    }

    public record SaveReq(
            @NotBlank(message = "角色名不能为空") @Size(max = 60, message = "角色名最长 60 字") String name,
            @Size(max = 20) String role,
            @Size(max = 5000, message = "角色描述过长") String description) {
    }

    public record VO(Long id, Long novelId, String name, String role, String description,
                     LocalDateTime createdAt) {
    }
}
