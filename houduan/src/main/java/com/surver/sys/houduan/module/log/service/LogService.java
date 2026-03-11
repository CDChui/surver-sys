package com.surver.sys.houduan.module.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import com.surver.sys.houduan.module.settings.service.SettingsServiceApi;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
@Profile("!nodeps")
public class LogService implements LogServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_TYPE_SYSTEM = "SYSTEM";
    private static final String LOG_TYPE_USER = "USER";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsServiceApi settingsService;

    public LogService(JdbcTemplate jdbcTemplate,
                      ObjectMapper objectMapper,
                      SettingsServiceApi settingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
    }

    public List<LogItemResponse> listLogs(String logType, String order) {
        String normalizedType = normalizeLogType(logType);
        String orderBy = normalizeOrder(order);
        if (normalizedType == null) {
            applyRetention(LOG_TYPE_SYSTEM);
            applyRetention(LOG_TYPE_USER);
        } else {
            applyRetention(normalizedType);
        }

        String roleCondition = buildRoleCondition(normalizedType);
        return jdbcTemplate.query(String.format("""
                SELECT l.id,
                       u.uid AS operator,
                       l.target_type AS module,
                       l.action AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(l.detail, '$.target')) AS target,
                       l.created_at,
                       l.ip,
                       l.user_agent
                FROM sys_audit_log l
                JOIN sys_user u ON u.id = l.actor_user_id
                WHERE 1 = 1 %s
                ORDER BY l.created_at %s, l.id %s
                """, roleCondition, orderBy, orderBy), (rs, rowNum) -> new LogItemResponse(
                rs.getLong("id"),
                rs.getString("operator"),
                rs.getString("module"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER),
                resolveTerminalType(rs.getString("user_agent")),
                str(rs.getString("ip"))
        ));
    }

    public void createLog(CreateLogRequest request) {
        Long actorUserId = findActorId(request.operator());
        String actorRole = findActorRole(actorUserId);
        String detailJson = writeDetailJson(request.target());
        ClientMeta clientMeta = resolveClientMeta();
        jdbcTemplate.update("""
                INSERT INTO sys_audit_log (actor_user_id, action, target_type, target_id, detail, ip, user_agent)
                VALUES (?, ?, ?, NULL, CAST(? AS JSON), ?, ?)
                """,
                actorUserId,
                request.action(),
                request.module(),
                detailJson,
                clientMeta.sourceIp(),
                clientMeta.userAgent()
        );
        applyRetention(resolveLogType(actorRole));
    }

    public void appendSystemLog(String operator, String module, String action, String target) {
        createLog(new CreateLogRequest(
                operator,
                module,
                action,
                target,
                nowText()
        ));
    }

    private Long findActorId(String operator) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM sys_user
                    WHERE uid = ?
                    ORDER BY id ASC
                    LIMIT 1
                    """, Long.class, operator);
        } catch (EmptyResultDataAccessException e) {
            return 1L;
        }
    }

    private String findActorRole(Long actorUserId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT role
                    FROM sys_user
                    WHERE id = ?
                    """, String.class, actorUserId);
        } catch (Exception e) {
            return "ROLE3";
        }
    }

    private String writeDetailJson(String target) {
        try {
            return objectMapper.writeValueAsString(Map.of("target", target));
        } catch (Exception e) {
            return "{\"target\":\"\"}";
        }
    }

    private void applyRetention(String logType) {
        if (logType == null) {
            return;
        }

        RetentionSettings retention = getRetentionSettings(logType);
        if (retention.keepDays() > 0) {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retention.keepDays());
            String roleCondition = buildRoleCondition(logType);
            jdbcTemplate.update(String.format("""
                    DELETE l
                    FROM sys_audit_log l
                    JOIN sys_user u ON u.id = l.actor_user_id
                    WHERE 1 = 1 %s
                      AND l.created_at < ?
                    """, roleCondition), Timestamp.valueOf(threshold));
        }

        if (retention.keepCount() > 0) {
            String roleCondition = buildRoleCondition(logType);
            jdbcTemplate.update(String.format("""
                    DELETE FROM sys_audit_log
                    WHERE id IN (
                        SELECT id FROM (
                            SELECT l.id
                            FROM sys_audit_log l
                            JOIN sys_user u ON u.id = l.actor_user_id
                            WHERE 1 = 1 %s
                            ORDER BY l.created_at DESC, l.id DESC
                            LIMIT 18446744073709551615 OFFSET ?
                        ) AS tmp
                    )
                    """, roleCondition), retention.keepCount());
        }
    }

    private RetentionSettings getRetentionSettings(String logType) {
        Map<String, Object> settings = settingsService.getSettings();
        if (LOG_TYPE_USER.equals(logType)) {
            return new RetentionSettings(
                    intVal(settings.get("userLogKeepDays"), 90),
                    intVal(settings.get("userLogKeepCount"), 2000)
            );
        }
        return new RetentionSettings(
                intVal(settings.get("systemLogKeepDays"), 180),
                intVal(settings.get("systemLogKeepCount"), 1000)
        );
    }

    private static String normalizeOrder(String order) {
        if ("ASC".equalsIgnoreCase(order)) {
            return "ASC";
        }
        return "DESC";
    }

    private static String normalizeLogType(String logType) {
        if ("USER".equalsIgnoreCase(logType)) {
            return LOG_TYPE_USER;
        }
        if ("SYSTEM".equalsIgnoreCase(logType)) {
            return LOG_TYPE_SYSTEM;
        }
        return null;
    }

    private static String resolveLogType(String role) {
        if ("ROLE1".equals(role)) {
            return LOG_TYPE_USER;
        }
        return LOG_TYPE_SYSTEM;
    }

    private static String buildRoleCondition(String logType) {
        if (LOG_TYPE_USER.equals(logType)) {
            return " AND u.role = 'ROLE1'";
        }
        if (LOG_TYPE_SYSTEM.equals(logType)) {
            return " AND u.role IN ('ROLE2','ROLE3')";
        }
        return "";
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static ClientMeta resolveClientMeta() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                if (request != null) {
                    String ip = normalizeIp(resolveClientIp(request));
                    String userAgent = normalizeUserAgent(resolveUserAgent(request));
                    return new ClientMeta(ip, userAgent);
                }
            }
        } catch (Exception ignored) {
            // ignore when request context unavailable
        }
        return new ClientMeta(null, null);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return (commaIndex >= 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? null : userAgent.trim();
    }

    private static String resolveTerminalType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("openharmony") || ua.contains("harmony") || ua.contains("arkweb") || ua.contains("hmos")) {
            return "鸿蒙";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "平板";
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "移动端";
        }
        return "PC";
    }

    private static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String text = ip.trim();
        if (text.isBlank()) {
            return null;
        }
        if (text.length() > 45) {
            return text.substring(0, 45);
        }
        return text;
    }

    private static String normalizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String text = userAgent.trim();
        if (text.isBlank()) {
            return null;
        }
        if (text.length() > 255) {
            return text.substring(0, 255);
        }
        return text;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record RetentionSettings(int keepDays, int keepCount) {
    }

    private record ClientMeta(String sourceIp, String userAgent) {
    }
}


