-- 语言风格：支持多选（以「、」连接）与不限字数的自定义文风描述，200 字上限不再够用
ALTER TABLE novels MODIFY COLUMN language_style LONGTEXT NULL;
