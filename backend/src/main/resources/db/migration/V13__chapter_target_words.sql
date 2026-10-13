-- 章节级目标字数：可按章覆盖全书均摊值（NULL = 沿用「总目标字数 / 章节数」）
ALTER TABLE chapter_outlines ADD COLUMN target_words INT NULL;