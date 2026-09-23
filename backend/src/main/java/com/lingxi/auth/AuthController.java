package com.lingxi.auth;

import com.lingxi.auth.dto.AuthDtos.AuthResp;
import com.lingxi.auth.dto.AuthDtos.LoginReq;
import com.lingxi.auth.dto.AuthDtos.RegisterReq;
import com.lingxi.auth.dto.AuthDtos.UserVO;
import com.lingxi.common.ApiResponse;
import com.lingxi.common.BizException;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new BizException(401, "登录已过期，请重新登录", 401);
        }
        return ApiResponse.ok(authService.me(user.getUsername()));
    }
}
