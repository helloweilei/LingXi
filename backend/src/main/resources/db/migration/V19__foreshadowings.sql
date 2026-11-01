-- 伏笔账本：跨章节悬念的结构化登记，解决「远距离伏笔掉出 AI 上下文视野」问题
-- OPEN=未回收（默认）；RESOLVED=已回收；ABANDONED=放弃不再回收
-- 状态为 OPEN 的条目会注入大纲与正文生成的 prompt（未回收伏笔清单段）
CREATE TABLE foreshadowings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    title VARCHAR(60) NOT NULL,
    content TEXT NULL,
    planted_order INT NULL,
    expected_order INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_order INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_foreshadowings_novel FOREIGN KEY (novel_id) REFERENCES novels (id)
);
CREATE INDEX idx_foreshadowings_novel_status ON foreshadowings (novel_id, status);
