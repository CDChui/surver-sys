package com.surver.sys.houduan.module.user.service;

import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.config.AdminInitProperties;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserItemResponse;
import com.surver.sys.houduan.module.user.model.UserModel;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.format.DateTimeFormatter;

@Service
public class UserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AdminInitProperties adminInitProperties;

    private final RowMapper<UserModel> userMapper = (rs, rowNum) -> {
        UserModel model = new UserModel();
        model.setId(rs.getLong("id"));
        model.setUsername(rs.getString("uid"));
        model.setRealName(rs.getString("display_name"));
        model.setRole(rs.getString("role"));
        model.setStatus(rs.getInt("status") == 1 ? "ENABLED" : "DISABLED");
        model.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER));
        model.setLocalAccount(rs.getInt("is_local") == 1);
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
                INSERT INTO sys_user (uid, login_name, display_name, password_hash, is_local, role, status)
                VALUES (?, ?, ?, ?, 1, 'ROLE3', 1)
                """,
                adminInitProperties.getUsername(),
                adminInitProperties.getUsername(),
                "系统管理员",
                passwordEncoder.encode(adminInitProperties.getPassword())
        );
    }

    public List<UserItemResponse> listUsers() {
        List<UserModel> users = jdbcTemplate.query("""
                SELECT id, uid, display_name, role, status, created_at, is_local
                FROM sys_user
                ORDER BY id ASC
                """, userMapper);
        return users.stream()
                .sorted(Comparator.comparing(UserModel::getId))
                .map(this::toResponse)
                .toList();
    }

    public UserItemResponse createUser(CreateUserRequest request) {
        String normalizedUsername = normalize(request.username());
        ensureUsernameNotExists(normalizedUsername);
        jdbcTemplate.update("""
                INSERT INTO sys_user (uid, login_name, display_name, password_hash, is_local, role, status)
                VALUES (?, ?, ?, NULL, 0, ?, ?)
                """,
                normalizedUsername,
                normalizedUsername,
                normalize(request.realName()),
                normalize(request.role()).toUpperCase(Locale.ROOT),
                toStatusFlag(request.status())
        );
        return toResponse(findByUsername(normalizedUsername)
                .orElseThrow(() -> new BizException(ErrorCode.SERVER_ERROR, "用户创建失败")));
    }

    public UserItemResponse updateUser(Long id, UpdateUserRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE sys_user
                SET display_name = ?, role = ?, status = ?
                WHERE id = ?
                """,
                normalize(request.realName()),
                normalize(request.role()).toUpperCase(Locale.ROOT),
                toStatusFlag(request.status()),
                id
        );
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toResponse(getById(id));
    }

    public void deleteUser(Long id) {
        UserModel model = getById(id);
        if ("ROLE3".equals(model.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "系统管理员不允许删除");
        }
        try {
            int deleted = jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
            if (deleted == 0) {
                throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "该用户已被业务数据引用，无法删除");
        }
    }

    public void updateRole(Long id, String role) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET role = ? WHERE id = ?",
                normalize(role).toUpperCase(Locale.ROOT), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    public void updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?",
                toStatusFlag(status), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    public Optional<UserModel> findByUsername(String username) {
        String normalized = normalize(username);
        try {
            UserModel model = jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, role, status, created_at, is_local
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

    public UserModel getById(Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, role, status, created_at, is_local
                    FROM sys_user
                    WHERE id = ?
                    """, userMapper, id);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    public boolean verifyLocalPassword(String username, String password) {
        String normalized = normalize(username);
        try {
            String passwordHash = jdbcTemplate.queryForObject("""
                    SELECT password_hash
                    FROM sys_user
                    WHERE uid = ? AND is_local = 1 AND status = 1
                    LIMIT 1
                    """, String.class, normalized);
            return passwordHash != null && passwordEncoder.matches(password, passwordHash);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    public Optional<UserModel> findLocalUserByUsername(String username) {
        String normalized = normalize(username);
        try {
            UserModel model = jdbcTemplate.queryForObject("""
                    SELECT id, uid, display_name, role, status, created_at, is_local
                    FROM sys_user
                    WHERE uid = ? AND is_local = 1
                    LIMIT 1
                    """, userMapper, normalized);
            return Optional.ofNullable(model);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public UserModel findOrCreateOauthUser(String username, String realName, String role) {
        Optional<UserModel> existed = findByUsername(username);
        if (existed.isPresent()) {
            UserModel model = existed.get();
            jdbcTemplate.update("""
                    UPDATE sys_user
                    SET display_name = ?, role = ?, status = 1, is_local = 0
                    WHERE id = ?
                    """, normalize(realName), normalize(role).toUpperCase(Locale.ROOT), model.getId());
            return getById(model.getId());
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user (uid, login_name, display_name, password_hash, is_local, role, status)
                VALUES (?, ?, ?, NULL, 0, ?, 1)
                """,
                normalize(username),
                normalize(username),
                normalize(realName),
                normalize(role).toUpperCase(Locale.ROOT));
        return findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.SERVER_ERROR, "第三方用户创建失败"));
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
                model.getRole(),
                model.getStatus(),
                model.getCreatedAt()
        );
    }

    private static int toStatusFlag(String status) {
        return "DISABLED".equalsIgnoreCase(normalize(status)) ? 0 : 1;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
