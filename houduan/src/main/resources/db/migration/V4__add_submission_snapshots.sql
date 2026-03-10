ALTER TABLE survey_answer
    ADD COLUMN survey_title_snapshot VARCHAR(200) NULL COMMENT '提交时问卷标题快照',
    ADD COLUMN survey_description_snapshot TEXT NULL COMMENT '提交时问卷描述快照',
    ADD COLUMN survey_schema_snapshot JSON NULL COMMENT '提交时问卷结构快照';

UPDATE survey_answer a
LEFT JOIN survey_info s ON s.id = a.survey_id
SET a.survey_title_snapshot = COALESCE(a.survey_title_snapshot, s.title, ''),
    a.survey_description_snapshot = COALESCE(a.survey_description_snapshot, s.description, ''),
    a.survey_schema_snapshot = COALESCE(a.survey_schema_snapshot, s.schema_data, JSON_ARRAY());

ALTER TABLE survey_answer
    MODIFY COLUMN survey_title_snapshot VARCHAR(200) NOT NULL COMMENT '提交时问卷标题快照',
    MODIFY COLUMN survey_description_snapshot TEXT NULL COMMENT '提交时问卷描述快照',
    MODIFY COLUMN survey_schema_snapshot JSON NOT NULL COMMENT '提交时问卷结构快照';

