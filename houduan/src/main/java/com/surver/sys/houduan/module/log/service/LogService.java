package com.surver.sys.houduan.module.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class LogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<LogItemResponse> listLogs() {
        return jdbcTemplate.query("""
                SELECT l.id,
                       u.uid AS operator,
                       l.target_type AS module,
                       l.action AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(l.detail, '$.target')) AS target,
                       l.created_at
                FROM sys_audit_log l
                JOIN sys_user u ON u.id = l.actor_user_id
                ORDER BY l.id DESC
                """, (rs, rowNum) -> new LogItemResponse(
                rs.getLong("id"),
                rs.getString("operator"),
                rs.getString("module"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER)
        )).stream()
                .sorted(Comparator.comparing(LogItemResponse::id))
                .toList();
    }

    public void createLog(CreateLogRequest request) {
        Long actorUserId = findActorId(request.operator());
        String detailJson = writeDetailJson(request.target());
        jdbcTemplate.update("""
                INSERT INTO sys_audit_log (actor_user_id, action, target_type, target_id, detail, ip, user_agent)
                VALUES (?, ?, ?, NULL, CAST(? AS JSON), NULL, NULL)
                """,
                actorUserId,
                request.action(),
                request.module(),
                detailJson
        );
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

    private String writeDetailJson(String target) {
        try {
            return objectMapper.writeValueAsString(Map.of("target", target));
        } catch (Exception e) {
            return "{\"target\":\"\"}";
        }
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
