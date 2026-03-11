package com.surver.sys.houduan.module.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.config.AdminInitProperties;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserItemResponse;
import com.surver.sys.houduan.module.user.model.UserModel;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("nodeps")
public class LocalUserService implements UserServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path USER_STORE_FILE = Path.of(".nodeps-data", "users.json").toAbsolutePath().normalize();
    private static final Path LEGACY_USER_STORE_FILE = Path.of("..", ".nodeps-data", "users.json").toAbsolutePath().normalize();
    private static final Set<String> DEFAULT_LOCAL_TEST_USERNAMES = Set.of(
            "admin",
            "teacher01",
            "student01",
            "counselor01",
            "student02"
    );

    private final ObjectMapper objectMapper;
    private final AdminInitProperties adminInitProperties;
    private final PasswordEncoder passwordEncoder;
    private final Map<Long, UserModel> userStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(10);

    public LocalUserService(ObjectMapper objectMapper,
                            AdminInitProperties adminInitProperties,
                            PasswordEncoder passwordEncoder) {
        this.objectMapper = objectMapper;
        this.adminInitProperties = adminInitProperties;
        this.passwordEncoder = passwordEncoder;
        loadUserStore();
        boolean migrated = migrateDefaultTestUsersToLocal();
        if (userStore.isEmpty()) {
            initUsers();
            migrated = true;
        }
        if (migrated) {
            saveUserStore();
        }
    }

    @Override
    public List<UserItemResponse> listUsers() {
        return userStore.values().stream()
                .sorted(Comparator.comparing(UserModel::getCreatedAt).reversed()
                        .thenComparing(UserModel::getId).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UserItemResponse createUser(CreateUserRequest request) {
        String username = normalize(request.username());
        String password = normalize(request.initialPassword());
        ensureUsernameNotExists(username);
        ensureValidPassword(password);

        UserModel model = new UserModel();
        model.setId(idGenerator.incrementAndGet());
        model.setUsername(username);
        model.setRealName(normalize(request.realName()));
        model.setRemark(normalize(request.remark()));
        model.setRole(normalize(request.role()).toUpperCase(Locale.ROOT));
        model.setStatus(normalizeStatus(request.status()));
        model.setCreatedAt(nowText());
        model.setLocalAccount(true);
        model.setPasswordHash(passwordEncoder.encode(password));
        userStore.put(model.getId(), model);
        saveUserStore();
        return toResponse(model);
    }

    @Override
    public UserItemResponse updateUser(Long id, UpdateUserRequest request) {
        UserModel model = getById(id);
        String nextRealName = normalize(request.realName());
        if (!model.isLocalAccount() && !Objects.equals(model.getRealName(), nextRealName)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "第三方用户的姓名不可修改");
        }

        if (model.isLocalAccount()) {
            model.setRealName(nextRealName);
        }
        model.setRemark(normalize(request.remark()));
        model.setRole(normalize(request.role()).toUpperCase(Locale.ROOT));
        model.setStatus(normalizeStatus(request.status()));
        saveUserStore();
        return toResponse(model);
    }

    @Override
    public void deleteUser(Long id) {
        UserModel model = getById(id);
        if ("ROLE3".equals(model.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "系统管理员不可删除");
        }
        userStore.remove(id);
        saveUserStore();
    }

    @Override
    public void updateRole(Long id, String role) {
        UserModel model = getById(id);
        model.setRole(normalize(role).toUpperCase(Locale.ROOT));
        saveUserStore();
    }

    @Override
    public void updateStatus(Long id, String status) {
        UserModel model = getById(id);
        model.setStatus(normalizeStatus(status));
        saveUserStore();
    }

    @Override
    public void resetLocalUserPassword(Long id, String newPassword) {
        UserModel model = getById(id);
        if (!model.isLocalAccount()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "仅本地用户支持重置密码");
        }

        String normalized = normalize(newPassword);
        ensureValidPassword(normalized);
        model.setPasswordHash(passwordEncoder.encode(normalized));
        saveUserStore();
    }

    @Override
    public void changeOwnLocalPassword(Long userId, String oldPassword, String newPassword) {
        UserModel model = getById(userId);
        if (!model.isLocalAccount()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "当前账号不是本地账号");
        }

        String oldNormalized = normalize(oldPassword);
        String newNormalized = normalize(newPassword);
        ensureValidPassword(newNormalized);

        if (oldNormalized.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请输入旧密码");
        }
        if (oldNormalized.equals(newNormalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "新密码不能与旧密码相同");
        }
        if (model.getPasswordHash() == null || model.getPasswordHash().isBlank() ||
                !passwordEncoder.matches(oldNormalized, model.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "旧密码不正确");
        }

        model.setPasswordHash(passwordEncoder.encode(newNormalized));
        saveUserStore();
    }

    @Override
    public Optional<UserModel> findByUsername(String username) {
        String normalized = normalize(username);
        return userStore.values().stream().filter(u -> normalized.equals(u.getUsername())).findFirst();
    }

    @Override
    public UserModel getById(Long id) {
        UserModel model = userStore.get(id);
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return model;
    }

    @Override
    public boolean verifyLocalPassword(String username, String password) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = normalize(password);
        if (normalizedPassword.isEmpty()) {
            return false;
        }

        Optional<UserModel> optional = findLocalUserByUsername(normalizedUsername)
                .filter(user -> "ENABLED".equals(user.getStatus()));
        if (optional.isEmpty()) {
            return false;
        }

        String hash = optional.get().getPasswordHash();
        return hash != null && !hash.isBlank() && passwordEncoder.matches(normalizedPassword, hash);
    }

    @Override
    public Optional<UserModel> findLocalUserByUsername(String username) {
        String normalized = normalize(username);
        return userStore.values().stream()
                .filter(u -> u.isLocalAccount() && normalized.equals(u.getUsername()))
                .findFirst();
    }

    @Override
    public UserModel findOrCreateOauthUser(String username, String realName, String role) {
        String normalizedUsername = normalize(username);
        Optional<UserModel> existedOauthUser = findOauthUserByUsername(normalizedUsername);
        if (existedOauthUser.isPresent()) {
            UserModel model = existedOauthUser.get();
            model.setRealName(normalize(realName));
            model.setRole(normalize(role).toUpperCase(Locale.ROOT));
            model.setStatus("ENABLED");
            model.setLocalAccount(false);
            model.setPasswordHash(null);
            model.setRemark(model.getRemark() == null ? "" : model.getRemark());
            saveUserStore();
            return model;
        }

        UserModel model = new UserModel();
        model.setId(idGenerator.incrementAndGet());
        model.setUsername(normalizedUsername);
        model.setRealName(normalize(realName));
        model.setRole(normalize(role).toUpperCase(Locale.ROOT));
        model.setStatus("ENABLED");
        model.setCreatedAt(nowText());
        model.setLocalAccount(false);
        model.setPasswordHash(null);
        model.setRemark("");
        userStore.put(model.getId(), model);
        saveUserStore();
        return model;
    }

    private Optional<UserModel> findOauthUserByUsername(String username) {
        return userStore.values().stream()
                .filter(u -> !u.isLocalAccount() && username.equals(u.getUsername()))
                .findFirst();
    }

    private void ensureUsernameNotExists(String username) {
        if (findByUsername(username).isPresent()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户名已存在");
        }
    }

    private UserItemResponse toResponse(UserModel model) {
        return new UserItemResponse(
                model.getId(),
                model.getUsername(),
                model.getRealName(),
                model.getRemark(),
                model.getRole(),
                model.getStatus(),
                model.getCreatedAt(),
                model.isLocalAccount()
        );
    }

    private void initUsers() {
        userStore.clear();
        List<UserModel> users = new ArrayList<>();

        UserModel admin = new UserModel();
        admin.setId(1L);
        admin.setUsername(adminInitProperties.getUsername());
        admin.setRealName("System Admin");
        admin.setRole("ROLE3");
        admin.setStatus("ENABLED");
        admin.setCreatedAt(nowText());
        admin.setLocalAccount(true);
        admin.setPasswordHash(passwordEncoder.encode(adminInitProperties.getPassword()));
        admin.setRemark("");
        users.add(admin);

        UserModel teacher = new UserModel();
        teacher.setId(2L);
        teacher.setUsername("teacher01");
        teacher.setRealName("Teacher Zhang");
        teacher.setRole("ROLE2");
        teacher.setStatus("ENABLED");
        teacher.setCreatedAt(nowText());
        teacher.setLocalAccount(true);
        teacher.setPasswordHash(passwordEncoder.encode("123456"));
        teacher.setRemark("");
        users.add(teacher);

        UserModel student = new UserModel();
        student.setId(4L);
        student.setUsername("student01");
        student.setRealName("Student Wang");
        student.setRole("ROLE1");
        student.setStatus("ENABLED");
        student.setCreatedAt(nowText());
        student.setLocalAccount(true);
        student.setPasswordHash(passwordEncoder.encode("123456"));
        student.setRemark("");
        users.add(student);

        users.forEach(u -> userStore.put(u.getId(), u));
        idGenerator.set(Math.max(10L, users.stream().map(UserModel::getId).filter(Objects::nonNull).max(Long::compareTo).orElse(10L)));
    }

    private void loadUserStore() {
        Path readPath = resolveUserStoreReadPath();
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

            userStore.clear();
            long maxId = 10L;
            List<Map<String, Object>> users = listOfMap(snapshot.get("users"));
            for (Map<String, Object> item : users) {
                Long userId = longValue(item.get("id"), null);
                if (userId == null) {
                    continue;
                }

                UserModel model = new UserModel();
                model.setId(userId);
                model.setUsername(str(item.get("username")));
                model.setRealName(str(item.get("realName")));
                model.setRole(str(item.getOrDefault("role", "ROLE1")));
                model.setStatus(normalizeStatus(str(item.getOrDefault("status", "ENABLED"))));
                model.setCreatedAt(str(item.getOrDefault("createdAt", nowText())));
                model.setLocalAccount(boolValue(item.get("localAccount")));
                model.setPasswordHash(str(item.get("passwordHash")));
                model.setRemark(str(item.get("remark")));

                if (model.isLocalAccount() && (model.getPasswordHash() == null || model.getPasswordHash().isBlank())) {
                    if (model.getUsername().equals(normalize(adminInitProperties.getUsername()))) {
                        model.setPasswordHash(passwordEncoder.encode(adminInitProperties.getPassword()));
                    }
                }

                userStore.put(model.getId(), model);
                if (model.getId() > maxId) {
                    maxId = model.getId();
                }
            }

            long nextId = Math.max(longValue(snapshot.get("nextUserId"), 10L), maxId);
            idGenerator.set(nextId);
            if (!Objects.equals(readPath, USER_STORE_FILE)) {
                saveUserStore();
            }
        } catch (Exception ignored) {
            // Ignore nodeps local persistence parse failures.
        }
    }

    private synchronized void saveUserStore() {
        try {
            Files.createDirectories(USER_STORE_FILE.getParent());

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("nextUserId", idGenerator.get());
            List<Map<String, Object>> users = userStore.values().stream()
                    .map(model -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", model.getId());
                        item.put("username", model.getUsername());
                        item.put("realName", model.getRealName());
                        item.put("role", model.getRole());
                        item.put("status", model.getStatus());
                        item.put("createdAt", model.getCreatedAt());
                        item.put("localAccount", model.isLocalAccount());
                        item.put("passwordHash", model.getPasswordHash());
                        item.put("remark", model.getRemark());
                        return item;
                    })
                    .sorted(Comparator.comparing(item -> longValue(item.get("id"), 0L)))
                    .toList();
            snapshot.put("users", users);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(USER_STORE_FILE.toFile(), snapshot);
        } catch (Exception ignored) {
            // Ignore nodeps local persistence write failures.
        }
    }

    private static Path resolveUserStoreReadPath() {
        if (Files.exists(USER_STORE_FILE)) {
            return USER_STORE_FILE;
        }
        if (Files.exists(LEGACY_USER_STORE_FILE)) {
            return LEGACY_USER_STORE_FILE;
        }
        return USER_STORE_FILE;
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

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(str(value));
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStatus(String value) {
        return "DISABLED".equalsIgnoreCase(normalize(value)) ? "DISABLED" : "ENABLED";
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private boolean migrateDefaultTestUsersToLocal() {
        boolean changed = false;
        for (UserModel model : userStore.values()) {
            String username = normalize(model.getUsername());
            if (!DEFAULT_LOCAL_TEST_USERNAMES.contains(username)) {
                continue;
            }

            if (!model.isLocalAccount()) {
                model.setLocalAccount(true);
                changed = true;
            }
            if (model.getPasswordHash() == null || model.getPasswordHash().isBlank()) {
                String defaultPassword = username.equals(normalize(adminInitProperties.getUsername()))
                        ? adminInitProperties.getPassword()
                        : "123456";
                model.setPasswordHash(passwordEncoder.encode(defaultPassword));
                changed = true;
            }
        }
        return changed;
    }

    private static void ensureValidPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码不能为空");
        }
        if (password.length() < 6 || password.length() > 64) {
            throw new BizException(ErrorCode.INVALID_PARAM, "密码长度需在 6 到 64 位之间");
        }
    }
}
