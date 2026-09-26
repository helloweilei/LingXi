package com.lingxi.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户级 AI 模型配置（每用户一份）。
 * provider=mock 表示使用内置示例引擎；provider=openai 时使用用户自填的 OpenAI 兼容配置（DeepSeek 等）。
 * 注意：api_key 明文落库，仅用于服务端转发；前端永不回显完整 Key。
 */
@Entity
@Table(name = "ai_config")
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String provider = "mock";

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl = "";

    @Column(name = "api_key", nullable = false, length = 255)
    private String apiKey = "";

    @Column(nullable = false, length = 100)
    private String model = "";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
