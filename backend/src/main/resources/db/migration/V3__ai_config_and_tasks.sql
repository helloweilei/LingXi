-- V3：用户级 AI 配置 + 自动创作任务
-- 说明：仅新增两张表，不修改既有表，可安全升级旧库。

-- 用户级 AI 模型配置（每用户一份；provider=mock 表示使用内置示例引擎）
CREATE TABLE ai_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL DEFAULT 'mock',
    base_url    VARCHAR(255) NOT NULL DEFAULT '',
    api_key     VARCHAR(255) NOT NULL DEFAULT '',
    model       VARCHAR(100) NOT NULL DEFAULT '',
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_config_user UNIQUE (user_id)
);

-- 自动创作任务（大纲 → 逐章生成 → 可选摘要；状态机：QUEUED/RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED）
CREATE TABLE writing_tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    mode            VARCHAR(20)  NOT NULL,           -- AUTO（全自动）/ CONFIRM（逐章确认）
    status          VARCHAR(20)  NOT NULL,
    params          TEXT,                             -- JSON：章节数/每章字数/自动大纲/自动摘要
    total_chapters  INT          NOT NULL DEFAULT 0,
    current_chapter INT          NOT NULL DEFAULT 0,  -- 已完成章节数（含待确认章）
    current_title   VARCHAR(120) NOT NULL DEFAULT '',
    error           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_writing_tasks_novel ON writing_tasks (novel_id);
CREATE INDEX idx_writing_tasks_user_status ON writing_tasks (user_id, status);
