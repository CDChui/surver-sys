package com.surver.sys.houduan.module.log.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import com.surver.sys.houduan.module.settings.service.SettingsServiceApi;
import com.surver.sys.houduan.module.user.model.UserModel;
import com.surver.sys.houduan.module.user.service.UserServiceApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("nodeps")
public class LocalLogService implements LogServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_STORE_FILE = Path.of(".nodeps-data", "logs.json").toAbsolutePath().normalize();
    private static final Path LEGACY_LOG_STORE_FILE = Path.of("..", ".nodeps-data", "logs.json").toAbsolutePath().normalize();
    private static final String LOG_TYPE_SYSTEM = "SYSTEM";
    private static final String LOG_TYPE_USER = "USER";

    private final ObjectMapper objectMapper;
    private final SettingsServiceApi settingsService;
    private final UserServiceApi userService;
    private final AtomicLong idGenerator = new AtomicLong(1000);
    private final CopyOnWriteArrayList<LogItemResponse> logs = new CopyOnWriteArrayList<>();

    public LocalLogService(ObjectMapper objectMapper,
                           SettingsServiceApi settingsService,
                           UserServiceApi userService) {
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.userService = userService;
        loadLogStore();
    }

    @Override
    public List<LogItemResponse> listLogs(String logType, String order) {
        String normalizedType = normalizeLogType(logType);
        if (normalizedType == null) {
            applyRetention(LOG_TYPE_SYSTEM);
            applyRetention(LOG_TYPE_USER);
        } else {
            applyRetention(normalizedType);
        }

        List<LogItemResponse> filtered = logs.stream()
                .filter(item -> normalizedType == null || normalizedType.equals(resolveLogTypeByOperator(item.operator())))
                .toList();
        return sortLogs(filtered, normalizeOrder(order));
    }

    @Override
    public void createLog(CreateLogRequest request) {
        ClientMeta clientMeta = resolveClientMeta();
        String terminalType = resolveTerminalType(clientMeta.userAgent());
        logs.add(new LogItemResponse(
                idGenerator.incrementAndGet(),
                request.operator(),
                request.module(),
                request.action(),
                request.target(),
                request.createdAt() == null || request.createdAt().isBlank() ? nowText() : request.createdAt(),
                terminalType,
                clientMeta.sourceIp()
        ));
        applyRetention(resolveLogTypeByOperator(request.operator()));
        saveLogStore();
    }

    @Override
    public void appendSystemLog(String operator, String module, String action, String target) {
        createLog(new CreateLogRequest(operator, module, action, target, nowText()));
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private void loadLogStore() {
        Path readPath = resolveLogStoreReadPath();
        if (!Files.exists(readPath)) {
            return;
        }

        try {
            Map<String, Object> snapshot = objectMapper.readValue(
                    readPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            if (snapshot == null) {
                return;
            }

            logs.clear();
            long maxId = 1000L;
            List<Map<String, Object>> items = listOfMap(snapshot.get("logs"));
            List<LogItemResponse> loaded = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Long id = longValue(item.get("id"), null);
                if (id == null) {
                    continue;
                }
                loaded.add(new LogItemResponse(
                        id,
                        str(item.get("operator")),
                        str(item.get("module")),
                        str(item.get("action")),
                        str(item.get("target")),
                        str(item.getOrDefault("createdAt", nowText())),
                        str(item.getOrDefault("terminalType", "未知")),
                        str(item.getOrDefault("sourceIp", ""))
                ));
                if (id > maxId) {
                    maxId = id;
                }
            }
            loaded.sort(Comparator.comparing(LogItemResponse::id));
            logs.addAll(loaded);

            long nextId = Math.max(longValue(snapshot.get("nextLogId"), 1000L), maxId);
            idGenerator.set(nextId);
            if (!Objects.equals(readPath, LOG_STORE_FILE)) {
                saveLogStore();
            }
        } catch (Exception ignored) {
            // Ignore local log load errors in nodeps mode.
        }
    }

    private List<LogItemResponse> sortLogs(List<LogItemResponse> items, String order) {
        Comparator<LogItemResponse> comparator =
                Comparator.comparing(LogItemResponse::createdAt)
                        .thenComparing(LogItemResponse::id);
        if ("DESC".equals(order)) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).toList();
    }

    private void applyRetention(String logType) {
        if (logType == null) {
            return;
        }

        RetentionSettings retention = getRetentionSettings(logType);
        List<LogItemResponse> targetLogs = logs.stream()
                .filter(item -> logType.equals(resolveLogTypeByOperator(item.operator())))
                .toList();

        List<LogItemResponse> retained = new ArrayList<>(targetLogs);
        if (retention.keepDays() > 0) {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retention.keepDays());
            retained = retained.stream()
                    .filter(item -> !parseCreatedAt(item.createdAt()).isBefore(threshold))
                    .toList();
        }

        if (retention.keepCount() > 0 && retained.size() > retention.keepCount()) {
            retained = sortLogs(retained, "DESC").stream()
                    .limit(retention.keepCount())
                    .toList();
        }

        if (retained.size() == targetLogs.size()) {
            return;
        }

        List<Long> retainedIds = retained.stream().map(LogItemResponse::id).toList();
        logs.removeIf(item ->
                logType.equals(resolveLogTypeByOperator(item.operator())) && !retainedIds.contains(item.id()));
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

    private String resolveLogTypeByOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return LOG_TYPE_SYSTEM;
        }
        Optional<UserModel> user = userService.findByUsername(operator);
        if (user.isPresent() && "ROLE1".equals(user.get().getRole())) {
            return LOG_TYPE_USER;
        }
        return LOG_TYPE_SYSTEM;
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

    private static String normalizeOrder(String order) {
        if ("ASC".equalsIgnoreCase(order)) {
            return "ASC";
        }
        return "DESC";
    }

    private static LocalDateTime parseCreatedAt(String value) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
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
    private synchronized void saveLogStore() {
        try {
            Files.createDirectories(LOG_STORE_FILE.getParent());

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("nextLogId", idGenerator.get());
            List<Map<String, Object>> items = logs.stream()
                    .sorted(Comparator.comparing(LogItemResponse::id))
                    .map(item -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", item.id());
                        row.put("operator", item.operator());
                        row.put("module", item.module());
                        row.put("action", item.action());
                        row.put("target", item.target());
                        row.put("createdAt", item.createdAt());
                        row.put("terminalType", item.terminalType());
                        row.put("sourceIp", item.sourceIp());
                        return row;
                    })
                    .toList();
            snapshot.put("logs", items);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(LOG_STORE_FILE.toFile(), snapshot);
        } catch (Exception ignored) {
            // nodeps 模式忽略本地日志保存错误
        }

    }

    private static Path resolveLogStoreReadPath() {
        if (Files.exists(LOG_STORE_FILE)) {
            return LOG_STORE_FILE;
        }
        if (Files.exists(LEGACY_LOG_STORE_FILE)) {
            return LEGACY_LOG_STORE_FILE;
        }
        return LOG_STORE_FILE;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private static Long longValue(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMap(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
    }

    private record RetentionSettings(int keepDays, int keepCount) {
    }

    private record ClientMeta(String sourceIp, String userAgent) {
    }
}



