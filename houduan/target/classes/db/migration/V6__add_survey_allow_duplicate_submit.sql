ALTER TABLE survey_info
    ADD COLUMN allow_duplicate_submit TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许重复提交' AFTER schema_data;

UPDATE survey_info
SET allow_duplicate_submit = COALESCE((SELECT allow_duplicate_submit FROM sys_settings WHERE id = 1), 0);
