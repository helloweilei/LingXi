-- 记录该引导会话选用后生成的作品，便于历史列表直接展示作品标题并跳转
ALTER TABLE creative_intents ADD COLUMN novel_id BIGINT NULL;
CREATE INDEX idx_creative_intents_novel ON creative_intents (novel_id);