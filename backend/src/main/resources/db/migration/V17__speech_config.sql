-- 用户级语音识别配置（每用户一份）：browser = 浏览器自带识别；xfyun = 科大讯飞语音听写；aliyun = 阿里云百炼 Paraformer
CREATE TABLE speech_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL DEFAULT 'browser',
    app_id      VARCHAR(100) NOT NULL DEFAULT '',
    api_key     VARCHAR(255) NOT NULL DEFAULT '',
    api_secret  VARCHAR(255) NOT NULL DEFAULT '',
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_speech_config_user UNIQUE (user_id)
);
