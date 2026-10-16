-- 人物关系：同一作品内两个角色之间的一条有向关系（如 师徒 / 宿敌 / 恋人），用于人物关系图谱
CREATE TABLE character_relations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id    BIGINT      NOT NULL,
    source_id   BIGINT      NOT NULL,
    target_id   BIGINT      NOT NULL,
    label       VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_cr_novel ON character_relations (novel_id);
