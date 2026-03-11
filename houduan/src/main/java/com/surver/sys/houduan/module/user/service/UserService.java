package com.surver.sys.houduan.module.user.service;

import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.config.AdminInitProperties;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserItemResponse;
import com.surver.sys.houduan.module.user.model.UserModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Profile("!nodeps")
public class UserService implements UserServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AdminInitProperties adminInitProperties;

    private final RowMapper<UserModel> userMapper = (rs, rowNum) -> {
        UserModel model = new UserModel();
        model.setId(rs.getLong("id"));
        model.setUsername(rs.getString("uid"));
        model.setRealName(rs.getString("display_name"));
        model.setRemark(rs.getString("remark"));
        model.setRole(rs.getString("role"));
        model.setStatus(rs.getInt("status") == 1 ? "ENABLED" : "DISABLED");
        model.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER));
        model.setLocalAccount(rs.getInt("is_local") == 1);
        model.setPasswordHash(rs.getString("password_hash"));
        return model;
    };

    public UserService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
                       AdminInitProperties adminInitProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminInitProperties = adminInitProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initLocalAdminIfAbsent() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user WHERE is_local = 1", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user (uid, login_name, display_name, remark, password_hash, is_local, role, status)
                VALUES (?, ?, ?, ?, ?, 1, 'ROLE3', 1)
                """,
                adminInitProperties.getUsername(),
                adminInitProperties.getUsername(),
                "系统管理员",
                "",
                passwordEncoder.encode(adminInitProperties.getPassword())
        );
    }

    @Override
    public List<UserItemResponse> listUsers() {
        List<UserModel> users = jdbcTemplate.query("""
                SELECT id, uid, display_name, remark, role, status, created_at, is_local, password_hash
                FROM sys_user
                ORDER BY created_at DESC, id DESC
                """, userMapper);
        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UserItemResponse createUser(CreateUserRequest request) {
        String normalizedUsername = normalize(request.username());
        String normalizedRealName = normalize(request.realName());
        String normalizedRemark = normalize(request.remark());
        String normalizedPassword = normalize(request.initialPassword());

        ensureUsernameNotExists(normalizedUsername);
        ensureValidPassword(normalizedPassword);

        jdbcTemplate.update("""
                INSERT INTO sys_user (uid, login_name, display_name, remark, password_hash, is_local, role, status)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """,
                normalizedUsername,
                normalizedUsername,
                normalizedRealName,
                normalizedRemark,
                passwordEncoder.encode(normalizedPassword),
                normalize(request.role()).toUpperCase(Locale.ROOT),
                toStatusFlag(request.status())
        );
        return toResponse(findLocalUserByUsername(normalizedUsername)
                .orElseThrow(() -> new BizException(ErrorCode.SERVER_ERROR, "创建用户失败")));
    }

    @Override
    public UserItemResponse updateUser(Long id, UpdateUserRequest request) {
        UserModel model = getById(id);
        String nextRealName = normalize(request.realName());
        String nextRemark = normalize(request.remark());
        if (!model.isLocalAccount() && !nextRealName.equals(model.getRealName())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "第三方用户的姓名不可修改");
        }

        String persistedRealName = model.isLocalAccount() ? nextRealName : model.getRealName();
        int updated = jdbcTemplate.update("""
                UPDATE sys_user
                SET display_name = ?, remark = ?, role = ?, status = ?
                WHERE id = ?
                """,
                persistedRealName,
                nextRemark,
                normalize(request.role()).toUpperCase(Locale.ROOT),
                toStatusFlag(request.status()),
                id
        );
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toResponse(getById(id));
    }

    @Override
    public void deleteUser(Long id) {
        UserModel model = getById(id);
        if ("ROLE3".equals(model.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "系统管理员不可删除");
        }
        try {
            int deleted = jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
            if (deleted == 0) {
                throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户已被业务数据引用，无法删除");
        }
    }

    @Override
    public void updateRole(Long id, String role) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET role = ? WHERE id = ?",
                normalize(role).toUpperCase(Locale.ROOT), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public void updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?",
                toStatusFlag(status), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public void resetLocalUserPassword(Long id, String newPassword) {
        UserModel model = getById(id);
        if (!model.isLocalAccount()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "仅本地用户支持重置密码");
        }

        String normalized = normalize(newPassword);
        ensureValidPassword(normalized);

        int updated = jdbcTemplate.update("""
                UPDATE sys_user
                SET password_hash = ?
                WHERE id = ? AND is_local = 1
                """, passwordEncoder.encode(normalized), id);

        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
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

        String passwordHash = model.getPasswordHash();
        if (passwordHash == null || passwordHash.isBlank() || !passwordEncoder.matches(oldNormalized, passwordHash)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "旧密码不正确");
        }

        jdbcTemplate.update("""
                UPDATE sys_user
                SET password_hash = ?
                WHERE id = ? AND is_local = 1
                """, passwordEncoder.encode(newNormalized), userId);
    }

    @Override
    public Optional<UserModel> findByUsername(String username) {
        String normalized = normalize(username);
        try {
            UserModel model = jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, remark, role, status, created_at, is_local, password_hash
                    FROM sys_user
                    WHERE uid = ?
                    ORDER BY id ASC
                    LIMIT 1
                    """, userMapper, normalized);
            return Optional.ofNullable(model);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public UserModel getById(Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, remark, role, status, created_at, is_local, password_hash
                    FROM sys_user
                    WHERE id = ?
                    """, userMapper, id);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public boolean verifyLocalPassword(String username, String password) {
        String normalized = normalize(username);
        String normalizedPassword = normalize(password);
        if (normalizedPassword.isEmpty()) {
            return false;
        }

        try {
            String passwordHash = jdbcTemplate.queryForObject("""
                    SELECT password_hash
                    FROM sys_user
                    WHERE uid = ? AND is_local = 1 AND status = 1
                    ORDER BY id ASC
                    LIMIT 1
                    """, String.class, normalized);
            return passwordHash != null && passwordEncoder.matches(normalizedPassword, passwordHash);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    @Override
    public Optional<UserModel> findLocalUserByUsername(String username) {
        String normalized = normalize(username);
        try {
            UserModel model = jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, remark, role, status, created_at, is_local, password_hash
                    FROM sys_user
                    WHERE uid = ? AND is_local = 1
                    ORDER BY id ASC
                    LIMIT 1
                    """, userMapper, normalized);
            return Optional.ofNullable(model);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public UserModel findOrCreateOauthUser(String username, String realName, String role) {
        String normalizedUsername = normalize(username);
        Optional<UserModel> existedOauthUser = findOauthUserByUsername(normalizedUsername);
        if (existedOauthUser.isPresent()) {
            UserModel model = existedOauthUser.get();
            jdbcTemplate.update("""
                    UPDATE sys_user
                    SET display_name = ?, role = ?, status = 1, is_local = 0
                    WHERE id = ?
                    """, normalize(realName), normalize(role).toUpperCase(Locale.ROOT), model.getId());
            return getById(model.getId());
        }

        jdbcTemplate.update("""
                INSERT INTO sys_user (uid, login_name, display_name, remark, password_hash, is_local, role, status)
                VALUES (?, ?, ?, ?, NULL, 0, ?, 1)
                """,
                normalizedUsername,
                normalizedUsername,
                normalize(realName),
                "",
                normalize(role).toUpperCase(Locale.ROOT));

        return findOauthUserByUsername(normalizedUsername)
                .orElseThrow(() -> new BizException(ErrorCode.SERVER_ERROR, "创建第三方用户失败"));
    }

    private Optional<UserModel> findOauthUserByUsername(String username) {
        try {
            UserModel model = jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, remark, role, status, created_at, is_local, password_hash
                    FROM sys_user
                    WHERE uid = ? AND is_local = 0
                    ORDER BY id ASC
                    LIMIT 1
                    """, userMapper, username);
            return Optional.ofNullable(model);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private void ensureUsernameNotExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE uid = ?",
                Integer.class,
                username
        );
        if (count != null && count > 0) {
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

    private static int toStatusFlag(String status) {
        return "DISABLED".equalsIgnoreCase(normalize(status)) ? 0 : 1;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
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
