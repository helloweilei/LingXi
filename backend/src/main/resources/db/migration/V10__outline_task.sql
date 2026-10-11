-- 章节大纲批量生成任务：后台异步执行，避免同步串行调用 LLM 导致 HTTP 超时
-- 注意：仅对已存在的章节生成大纲，不会创建章节
CREATE TABLE outline_tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  novel_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  chapter_ids VARCHAR(1000) NOT NULL COMMENT '待生成大纲的章节 id，逗号分隔',
  total INT NOT NULL DEFAULT 0,
  current_index INT NOT NULL DEFAULT 0,
  current_title VARCHAR(120) NOT NULL DEFAULT '',
  succeeded INT NOT NULL DEFAULT 0,
  failed INT NOT NULL DEFAULT 0,
  error TEXT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE INDEX idx_outline_tasks_novel ON outline_tasks (novel_id, id);