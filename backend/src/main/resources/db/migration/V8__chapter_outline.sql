-- 章节大纲（写作前规划）：将“章节摘要”重构为“章节大纲”，用于创作前规划、指导正文、避免收尾失控
-- 兼容 H2(MODE=MySQL) 与 MySQL 8；既有库通过 baseline-on-migrate 跳过本脚本

ALTER TABLE chapter_summaries RENAME TO chapter_outlines;

ALTER TABLE chapter_outlines RENAME COLUMN summary TO outline;
