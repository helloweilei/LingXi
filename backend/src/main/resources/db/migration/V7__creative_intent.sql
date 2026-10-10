-- 启发式创作引导：记录一次「从想法到作品」的引导会话（问答种子 / 创意提案 / 选用结果）
CREATE TABLE creative_intents (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    seed_json     LONGTEXT,
    proposal_json LONGTEXT,
    selected_id   INT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
