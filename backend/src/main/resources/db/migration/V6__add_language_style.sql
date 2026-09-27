-- 小说语言风格：供 AI 写作时约束文风（如：轻松幽默/古典文风），空串表示未设置
ALTER TABLE novels ADD COLUMN language_style VARCHAR(50) NULL;
