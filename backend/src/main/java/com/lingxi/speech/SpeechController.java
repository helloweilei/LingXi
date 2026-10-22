package com.lingxi.speech;

import com.lingxi.common.ApiResponse;
import com.lingxi.speech.dto.SpeechDtos.ConfigReq;
import com.lingxi.speech.dto.SpeechDtos.ConfigVO;
import com.lingxi.speech.dto.SpeechDtos.RecognizeVO;
import com.lingxi.user.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    @GetMapping("/config")
    public ApiResponse<ConfigVO> get(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(speechService.get(user));
    }

    @PutMapping("/config")
    public ApiResponse<ConfigVO> save(@AuthenticationPrincipal User user, @Valid @RequestBody ConfigReq req) {
        return ApiResponse.ok(speechService.save(user, req));
    }

    /** 请求体为原始 PCM：16kHz / 16bit / 单声道 / 小端 */
    @PostMapping(value = "/recognize", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<RecognizeVO> recognize(@AuthenticationPrincipal User user, @RequestBody byte[] pcm) {
        return ApiResponse.ok(new RecognizeVO(speechService.recognize(user, pcm)));
    }
}
