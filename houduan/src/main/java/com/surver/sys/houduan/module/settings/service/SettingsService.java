package com.surver.sys.houduan.module.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!nodeps")
public class SettingsService implements SettingsServiceApi {

    private static final long SETTINGS_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SettingsService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getSettings() {
        ensureSettingsRow();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT system_name, system_domain, default_page_size, enable_log,
                       enable_resume_draft, allow_duplicate_submit, admin_logo, user_home_logo, title_logo,
                       system_log_keep_days, system_log_keep_count, user_log_keep_days, user_log_keep_count,
                       oauth_json, auth_integration_json
                FROM sys_settings
                WHERE id = ?
                """, SETTINGS_ID);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemName", str(row.get("system_name")));
        result.put("systemDomain", str(row.get("system_domain")));
        result.put("defaultPageSize", intVal(row.get("default_page_size"), 20));
        result.put("enableLog", boolVal(row.get("enable_log")));
        result.put("enableResumeDraft", boolVal(row.get("enable_resume_draft")));
        result.put("allowDuplicateSubmit", boolVal(row.get("allow_duplicate_submit")));
        result.put("adminLogo", str(row.get("admin_logo")));
        result.put("userHomeLogo", str(row.get("user_home_logo")));
        result.put("titleLogo", str(row.get("title_logo")));
        result.put("systemLogKeepDays", intVal(row.get("system_log_keep_days"), 180));
        result.put("systemLogKeepCount", intVal(row.get("system_log_keep_count"), 1000));
        result.put("userLogKeepDays", intVal(row.get("user_log_keep_days"), 90));
        result.put("userLogKeepCount", intVal(row.get("user_log_keep_count"), 2000));
        result.put("oauth", parseJsonToMap(row.get("oauth_json"), (Map<String, Object>) defaultSettings().get("oauth")));
        result.put("authIntegration", parseJsonToMap(row.get("auth_integration_json"),
                (Map<String, Object>) defaultSettings().get("authIntegration")));
        return result;
    }

    public void saveSettings(Map<String, Object> settings) {
        ensureSettingsRow();
        Map<String, Object> merged = deepCopy(getSettings());
        merged.putAll(settings);
        Object oauthValue = settings.get("oauth");
        Object authIntegrationValue = settings.get("authIntegration");
        if (oauthValue instanceof Map<?, ?> oauthMap) {
            merged.put("oauth", deepCopy((Map<String, Object>) oauthMap));
        }
        if (authIntegrationValue instanceof Map<?, ?> aiMap) {
            merged.put("authIntegration", deepCopy((Map<String, Object>) aiMap));
        }

        try {
            jdbcTemplate.update("""
                    UPDATE sys_settings
                    SET system_name = ?, system_domain = ?, default_page_size = ?, enable_log = ?,
                        enable_resume_draft = ?, allow_duplicate_submit = ?, admin_logo = ?, user_home_logo = ?, title_logo = ?,
                        system_log_keep_days = ?, system_log_keep_count = ?, user_log_keep_days = ?, user_log_keep_count = ?,
                        oauth_json = ?, auth_integration_json = ?
                    WHERE id = ?
                    """,
                    str(merged.get("systemName")),
                    str(merged.get("systemDomain")),
                    intVal(merged.get("defaultPageSize"), 20),
                    boolVal(merged.get("enableLog")) ? 1 : 0,
                    boolVal(merged.get("enableResumeDraft")) ? 1 : 0,
                    boolVal(merged.get("allowDuplicateSubmit")) ? 1 : 0,
                    str(merged.get("adminLogo")),
                    str(merged.get("userHomeLogo")),
                    str(merged.get("titleLogo")),
                    intVal(merged.get("systemLogKeepDays"), 180),
                    intVal(merged.get("systemLogKeepCount"), 1000),
                    intVal(merged.get("userLogKeepDays"), 90),
                    intVal(merged.get("userLogKeepCount"), 2000),
                    objectMapper.writeValueAsString(merged.get("oauth")),
                    objectMapper.writeValueAsString(merged.get("authIntegration")),
                    SETTINGS_ID
            );
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "系统设置保存失败: " + e.getMessage());
        }
    }

    private void ensureSettingsRow() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_settings WHERE id = ?", Integer.class, SETTINGS_ID);
        if (count != null && count > 0) {
            return;
        }
        Map<String, Object> defaults = defaultSettings();
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_settings (
                        id, system_name, system_domain, default_page_size, enable_log,
                        enable_resume_draft, allow_duplicate_submit, admin_logo, user_home_logo, title_logo,
                        system_log_keep_days, system_log_keep_count, user_log_keep_days, user_log_keep_count,
                        oauth_json, auth_integration_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    SETTINGS_ID,
                    defaults.get("systemName"),
                    defaults.get("systemDomain"),
                    defaults.get("defaultPageSize"),
                    boolVal(defaults.get("enableLog")) ? 1 : 0,
                    boolVal(defaults.get("enableResumeDraft")) ? 1 : 0,
                    boolVal(defaults.get("allowDuplicateSubmit")) ? 1 : 0,
                    defaults.get("adminLogo"),
                    defaults.get("userHomeLogo"),
                    defaults.get("titleLogo"),
                    defaults.get("systemLogKeepDays"),
                    defaults.get("systemLogKeepCount"),
                    defaults.get("userLogKeepDays"),
                    defaults.get("userLogKeepCount"),
                    objectMapper.writeValueAsString(defaults.get("oauth")),
                    objectMapper.writeValueAsString(defaults.get("authIntegration"))
            );
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "系统设置初始化失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(Object rawJson, Map<String, Object> fallback) {
        if (rawJson == null) {
            return deepCopy(fallback);
        }
        try {
            String text = String.valueOf(rawJson);
            if (text.isBlank()) {
                return deepCopy(fallback);
            }
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return deepCopy(fallback);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> defaultSettings() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("systemName", "问卷调查系统");
        root.put("systemDomain", "");
        root.put("defaultPageSize", 20);
        root.put("enableLog", true);
        root.put("enableResumeDraft", true);
        root.put("allowDuplicateSubmit", false);
        root.put("adminLogo", "");
        root.put("userHomeLogo", "");
        root.put("titleLogo", "");
        root.put("systemLogKeepDays", 180);
        root.put("systemLogKeepCount", 1000);
        root.put("userLogKeepDays", 90);
        root.put("userLogKeepCount", 2000);

        Map<String, Object> oauth = new LinkedHashMap<>();
        oauth.put("enabled", false);
        oauth.put("environment", "CUSTOM");
        oauth.put("authDomain", "");
        oauth.put("clientId", "");
        oauth.put("clientSecret", "");
        oauth.put("redirectUri", "");
        oauth.put("logoutRedirectUri", "");
        root.put("oauth", oauth);

        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", "iam-default");
        provider.put("name", "校园统一身份认证(IAM)");
        provider.put("protocol", "IAM_TEMPLATE");
        provider.put("enabled", false);
        provider.put("priority", 1);
        provider.put("environment", "CUSTOM");
        provider.put("authDomain", "");
        provider.put("clientId", "");
        provider.put("clientSecret", "");
        provider.put("scope", "openid profile");
        provider.put("redirectUri", "");
        provider.put("logoutRedirectUri", "");
        provider.put("authorizeUrl", "");
        provider.put("tokenUrl", "");
        provider.put("userInfoUrl", "");
        provider.put("refreshUrl", "");
        provider.put("revokeUrl", "");
        provider.put("userIdField", "employeeNumber");
        provider.put("realNameField", "displayName");
        provider.put("emailField", "mail");

        Map<String, Object> authIntegration = new LinkedHashMap<>();
        authIntegration.put("loginMode", "LOCAL_ONLY");
        authIntegration.put("defaultProviderId", "iam-default");
        authIntegration.put("autoCreateUser", true);
        authIntegration.put("defaultRole", "ROLE1");
        List<Map<String, Object>> providers = new ArrayList<>();
        providers.add(provider);
        authIntegration.put("providers", providers);
        root.put("authIntegration", authIntegration);

        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                result.put(entry.getKey(), deepCopy((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                result.put(entry.getKey(), deepCopyList(listValue));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static List<Object> deepCopyList(List<?> source) {
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map<?, ?> mapItem) {
                result.add(deepCopy((Map<String, Object>) mapItem));
            } else if (item instanceof List<?> listItem) {
                result.add(deepCopyList(listItem));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean boolVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
