-- 大纲独立化：章节大纲是「创作前的规划行」，不应依附章节而存在。
-- 章节是在生成正文时，才从对应的大纲行落地创建出来的产物。
--
-- 本迁移：
-- 1) 给大纲加自己的 chapter_order（规划序号）与 title（规划章节名）
-- 2) 从既有章节回填历史大纲的序号与标题（迁移前大纲都挂在章节上）
-- 3) chapter_id 由 NOT NULL 改为可空：仅当该章正文已生成时才回填
--
-- 兼容 H2(MODE=MySQL) 与 MySQL 8：UPDATE ... JOIN 是 MySQL 专有语法，
-- 因此回填使用「相关子查询」写法（两者均支持，且子查询不涉及被更新的表本身）。

ALTER TABLE chapter_outlines ADD COLUMN chapter_order INT NULL;
ALTER TABLE chapter_outlines ADD COLUMN title VARCHAR(120) NULL;

-- 历史数据回填：沿用原章节的序号与标题
UPDATE chapter_outlines
SET chapter_order = (SELECT c.chapter_order FROM chapters c WHERE c.id = chapter_outlines.chapter_id)
WHERE chapter_order IS NULL;

UPDATE chapter_outlines
SET title = (SELECT c.title FROM chapters c WHERE c.id = chapter_outlines.chapter_id)
WHERE title IS NULL;

-- chapter_id 改为可空（大纲先于章节存在；生成正文后才回填）
ALTER TABLE chapter_outlines MODIFY COLUMN chapter_id BIGINT NULL;

-- 同一本书内按规划序号检索
CREATE INDEX idx_co_novel_order ON chapter_outlines (novel_id, chapter_order);
