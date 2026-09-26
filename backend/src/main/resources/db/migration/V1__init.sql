-- 灵犀创作 v1.0 初始表结构（与 v1 实体映射一致）
-- 说明：兼容 H2(MODE=MySQL) 与 MySQL 8；既有库通过 baseline-on-migrate 跳过本脚本

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE novels (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(80)  NOT NULL,
    description  VARCHAR(1000),
    genre        VARCHAR(30),
    status       VARCHAR(20),
    target_words INT,
    word_count   BIGINT       NOT NULL DEFAULT 0,
    outline      LONGTEXT,
    cover_color  VARCHAR(20),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE TABLE chapters (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id      BIGINT      NOT NULL,
    title         VARCHAR(120) NOT NULL,
    content       LONGTEXT,
    chapter_order INT         NOT NULL,
    word_count    BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL
);

CREATE TABLE novel_characters (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id     BIGINT      NOT NULL,
    name         VARCHAR(60) NOT NULL,
    role         VARCHAR(20),
    description  LONGTEXT,
    created_at   TIMESTAMP   NOT NULL
);

CREATE TABLE world_settings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id    BIGINT      NOT NULL,
    title       VARCHAR(80) NOT NULL,
    content     LONGTEXT,
    created_at  TIMESTAMP   NOT NULL
);
