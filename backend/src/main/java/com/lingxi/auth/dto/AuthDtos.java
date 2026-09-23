package com.lingxi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterReq(
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 30, message = "用户名长度需在 3-30 之间") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间") String password,
            @Size(max = 50) String nickname) {
    }

    public record LoginReq(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record UserVO(Long id, String username, String nickname) {
    }

    public record AuthResp(String token, UserVO user) {
    }
}
