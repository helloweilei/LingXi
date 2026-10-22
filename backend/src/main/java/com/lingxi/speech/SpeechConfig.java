package com.lingxi.speech;

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
 * 用户级语音识别配置（每用户一份）。
 * provider=browser 时前端使用浏览器自带识别，其余字段不生效；
 * xfyun 需要 app_id + api_key + api_secret；aliyun（百炼 DashScope）只需要 api_key。
 * 注意：密钥明文落库，仅用于服务端调用识别服务；前端永不回显完整密钥。
 */
@Entity
@Table(name = "speech_config")
public class SpeechConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String provider = "browser";

    @Column(name = "app_id", nullable = false, length = 100)
    private String appId = "";

    @Column(name = "api_key", nullable = false, length = 255)
    private String apiKey = "";

    @Column(name = "api_secret", nullable = false, length = 255)
    private String apiSecret = "";

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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
