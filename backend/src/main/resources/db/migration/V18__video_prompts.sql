-- 视频提示词持久化：生成过的可直接查看、重新生成（覆盖）或删除
-- chapter_id 为空表示全书宣传片提示词；同一目标至多保留最新一份
CREATE TABLE video_prompts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    mode VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_video_prompts_novel FOREIGN KEY (novel_id) REFERENCES novels (id)
);
CREATE INDEX idx_video_prompts_novel ON video_prompts (novel_id);
