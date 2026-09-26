-- V4: 自动创作任务记录其生成物（章节 id 列表、是否生成过大纲），供删除任务时移除生成内容
ALTER TABLE writing_tasks ADD COLUMN created_chapters VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE writing_tasks ADD COLUMN generated_outline BOOLEAN NOT NULL DEFAULT FALSE;
