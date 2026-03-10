ALTER TABLE sys_settings
    ADD COLUMN admin_logo LONGTEXT NULL COMMENT '管理后台 Logo',
    ADD COLUMN user_home_logo LONGTEXT NULL COMMENT '用户主页 Logo';
