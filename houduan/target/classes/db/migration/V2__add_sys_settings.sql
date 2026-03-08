CREATE TABLE IF NOT EXISTS sys_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    system_name VARCHAR(128) NOT NULL DEFAULT '问卷调查系统' COMMENT '系统名称',
    system_domain VARCHAR(255) NOT NULL DEFAULT '' COMMENT '系统域名',
    default_page_size INT NOT NULL DEFAULT 20 COMMENT '默认分页条数',
    enable_log TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否开启日志',
    enable_resume_draft TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否开启断点续答',
    allow_duplicate_submit TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许重复提交',
    oauth_json JSON NULL COMMENT '兼容旧 OAuth 结构',
    auth_integration_json JSON NULL COMMENT '新 authIntegration 结构',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';

INSERT INTO sys_settings (
    id,
    system_name,
    system_domain,
    default_page_size,
    enable_log,
    enable_resume_draft,
    allow_duplicate_submit,
    oauth_json,
    auth_integration_json
)
SELECT
    1,
    '问卷调查系统',
    '',
    20,
    1,
    1,
    0,
    JSON_OBJECT(
        'enabled', false,
        'environment', 'CUSTOM',
        'authDomain', '',
        'clientId', '',
        'clientSecret', '',
        'redirectUri', '',
        'logoutRedirectUri', ''
    ),
    JSON_OBJECT(
        'loginMode', 'LOCAL_ONLY',
        'defaultProviderId', 'iam-default',
        'autoCreateUser', true,
        'defaultRole', 'ROLE1',
        'providers', JSON_ARRAY(
            JSON_OBJECT(
                'id', 'iam-default',
                'name', '校园统一身份认证(IAM)',
                'protocol', 'IAM_TEMPLATE',
                'enabled', false,
                'priority', 1,
                'environment', 'CUSTOM',
                'authDomain', '',
                'clientId', '',
                'clientSecret', '',
                'scope', 'openid profile',
                'redirectUri', '',
                'logoutRedirectUri', '',
                'authorizeUrl', '',
                'tokenUrl', '',
                'userInfoUrl', '',
                'refreshUrl', '',
                'revokeUrl', '',
                'userIdField', 'employeeNumber',
                'realNameField', 'displayName',
                'emailField', 'mail'
            )
        )
    )
WHERE NOT EXISTS (SELECT 1 FROM sys_settings WHERE id = 1);
