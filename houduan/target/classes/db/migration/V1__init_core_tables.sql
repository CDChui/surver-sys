CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    uid VARCHAR(64) NOT NULL COMMENT 'IAM uid 或本地用户名',
    login_name VARCHAR(64) NULL COMMENT '学号/工号/账号',
    display_name VARCHAR(64) NULL COMMENT '姓名',
    password_hash VARCHAR(255) NULL COMMENT '本地用户密码哈希',
    is_local TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否本地用户',
    role VARCHAR(16) NOT NULL DEFAULT 'ROLE1' COMMENT 'ROLE1/ROLE2/ROLE3',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_uid_islocal (uid, is_local),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（本地/SSO 双轨）';

CREATE TABLE IF NOT EXISTS survey_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问卷ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    description TEXT NULL COMMENT '描述/说明',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED/DELETED',
    creator_user_id BIGINT NOT NULL COMMENT '创建人 sys_user.id',
    schema_data JSON NOT NULL COMMENT '问卷结构 JSON',
    quota_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启收集上限',
    quota_total INT NULL COMMENT '收集上限',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL COMMENT '软删时间',
    KEY idx_creator_status (creator_user_id, status),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷主表';

CREATE TABLE IF NOT EXISTS survey_auth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    survey_id BIGINT NOT NULL COMMENT '问卷ID',
    grantee_user_id BIGINT NOT NULL COMMENT '被授权人 sys_user.id',
    granted_by BIGINT NOT NULL COMMENT '授权人 sys_user.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_survey_grantee (survey_id, grantee_user_id),
    KEY idx_grantee (grantee_user_id),
    CONSTRAINT fk_auth_survey FOREIGN KEY (survey_id) REFERENCES survey_info(id),
    CONSTRAINT fk_auth_grantee FOREIGN KEY (grantee_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷授权表';

CREATE TABLE IF NOT EXISTS survey_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    survey_id BIGINT NOT NULL COMMENT '问卷ID',
    user_id BIGINT NOT NULL COMMENT '提交人 sys_user.id',
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    answer_data JSON NOT NULL COMMENT '答案 JSON',
    ip VARCHAR(45) NULL COMMENT '客户端IP',
    user_agent VARCHAR(255) NULL COMMENT 'UA',
    UNIQUE KEY uk_survey_user (survey_id, user_id),
    KEY idx_survey_time (survey_id, submit_time),
    CONSTRAINT fk_answer_survey FOREIGN KEY (survey_id) REFERENCES survey_info(id),
    CONSTRAINT fk_answer_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答卷表（唯一约束防刷）';

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL COMMENT '操作者 sys_user.id',
    action VARCHAR(64) NOT NULL COMMENT '动作',
    target_type VARCHAR(32) NOT NULL COMMENT '对象类型',
    target_id BIGINT NULL COMMENT '对象ID',
    detail JSON NULL COMMENT '补充信息',
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_actor_time (actor_user_id, created_at),
    KEY idx_action_time (action, created_at),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志（关键操作追踪）';
