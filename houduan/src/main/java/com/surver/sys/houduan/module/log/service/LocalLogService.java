package com.surver.sys.houduan.module.log.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("nodeps")
public class LocalLogService implements LogServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_STORE_FILE = Path.of(".nodeps-data", "logs.json").toAbsolutePath().normalize();
    private static final Path LEGACY_LOG_STORE_FILE = Path.of("..", ".nodeps-data", "logs.json").toAbsolutePath().normalize();

    private final ObjectMapper objectMapper;
    private final AtomicLong idGenerator = new AtomicLong(1000);
    private final CopyOnWriteArrayList<LogItemResponse> logs = new CopyOnWriteArrayList<>();

    public LocalLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadLogStore();
    }

    @Override
    public List<LogItemResponse> listLogs() {
        return logs.stream().toList();
    }

    @Override
    public void createLog(CreateLogRequest request) {
        logs.add(new LogItemResponse(
                idGenerator.incrementAndGet(),
                request.operator(),
                request.module(),
                request.action(),
                request.target(),
                request.createdAt() == null || request.createdAt().isBlank() ? nowText() : request.createdAt()
        ));
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
                        str(item.getOrDefault("createdAt", nowText()))
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
            // nodeps 下本地持久化失败不阻断服务启动
        }
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
                        return row;
                    })
                    .toList();
            snapshot.put("logs", items);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(LOG_STORE_FILE.toFile(), snapshot);
        } catch (Exception ignored) {
            // nodeps 下本地持久化失败不阻断主流程
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
}
