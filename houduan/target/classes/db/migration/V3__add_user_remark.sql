ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS remark VARCHAR(255) NULL COMMENT 'user remark' AFTER display_name;
