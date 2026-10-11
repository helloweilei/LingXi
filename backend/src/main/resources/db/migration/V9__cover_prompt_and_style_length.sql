-- 封面提示词：由 AI 依据书名/类型/简介/文风生成，可编辑后用于外部绘图或复现封面
ALTER TABLE novels ADD COLUMN cover_prompt VARCHAR(2000) NULL;

-- 语言风格：支持用户自由输入较长的文风描述（原 50 字过短）
ALTER TABLE novels MODIFY COLUMN language_style VARCHAR(200) NULL;