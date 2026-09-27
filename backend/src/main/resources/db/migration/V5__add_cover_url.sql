-- 小说封面：存上传后的静态资源路径（/uploads/xxx.png），空串表示未设置（前端用 coverColor 兜底占位）
ALTER TABLE novels ADD COLUMN cover_url VARCHAR(500) NULL;
