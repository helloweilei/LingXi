package com.lingxi.upload;

import com.lingxi.common.ApiResponse;
import com.lingxi.common.BizException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传：封面等静态资源落盘到 ./uploads，经 /uploads/** 静态映射访问。
 * 仅接受图片类型、单文件 ≤ 5MB（与 application.yml multipart 限制一致）。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的图片", 400);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(400, "图片不能超过 5MB", 400);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(400, "仅支持 jpg/png/webp/gif 图片", 400);
        }
        try {
            Files.createDirectories(uploadDir);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = uploadDir.resolve(name).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BizException(400, "非法文件名", 400);
            }
            file.transferTo(target);
            return ApiResponse.ok(Map.of("url", "/uploads/" + name));
        } catch (IOException e) {
            throw new BizException(500, "文件保存失败", 500);
        }
    }
}
