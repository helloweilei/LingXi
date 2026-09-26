package com.lingxi.ai;

import com.lingxi.ai.dto.AiDtos.ConfigReq;
import com.lingxi.ai.dto.AiDtos.ConfigVO;
import com.lingxi.ai.dto.AiDtos.TestVO;
import com.lingxi.common.ApiResponse;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping
    public ApiResponse<ConfigVO> get(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(aiConfigService.get(user));
    }

    @PutMapping
    public ApiResponse<ConfigVO> save(@AuthenticationPrincipal User user, @Valid @RequestBody ConfigReq req) {
        return ApiResponse.ok(aiConfigService.save(user, req));
    }

    /** 连通性测试（不保存配置，仅验证当前配置可用） */
    @GetMapping("/test")
    public ApiResponse<TestVO> test(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(aiConfigService.test(user));
    }
}
