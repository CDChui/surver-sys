ALTER TABLE sys_settings
    ADD COLUMN system_log_keep_days INT NOT NULL DEFAULT 180 COMMENT '系统日志保留天数',
    ADD COLUMN system_log_keep_count INT NOT NULL DEFAULT 1000 COMMENT '系统日志保留数量',
    ADD COLUMN user_log_keep_days INT NOT NULL DEFAULT 90 COMMENT '用户日志保留天数',
    ADD COLUMN user_log_keep_count INT NOT NULL DEFAULT 2000 COMMENT '用户日志保留数量';
