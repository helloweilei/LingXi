-- 灵犀创作 v1.0 增强：章节软删除 + 版本快照 + 记忆（章节摘要 / 故事脑）

-- 章节软删除标记（0=正常 1=已删除）
ALTER TABLE chapters ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 章节版本快照：保存章节历史，AI 写入/恢复前自动留档，可回滚
CREATE TABLE chapter_versions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chapter_id  BIGINT       NOT NULL,
    version_no  INT          NOT NULL,
    title       VARCHAR(120) NOT NULL,
    content     LONGTEXT     NOT NULL,
    word_count  BIGINT       NOT NULL DEFAULT 0,
    reason      VARCHAR(30),
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX idx_cv_chapter ON chapter_versions (chapter_id);

-- 章节摘要：每章生成后沉淀 150-200 字摘要，供 AI 跨章记忆
CREATE TABLE chapter_summaries (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id    BIGINT    NOT NULL,
    chapter_id  BIGINT    NOT NULL,
    summary     LONGTEXT  NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_cs_chapter UNIQUE (chapter_id)
);
CREATE INDEX idx_cs_novel ON chapter_summaries (novel_id);

-- 故事脑：整本书的动态剧情状态（伏笔/线索/人物弧），随章节推进由 AI 更新
CREATE TABLE story_memories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id    BIGINT    NOT NULL,
    story_brain LONGTEXT  NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_sm_novel UNIQUE (novel_id)
);
