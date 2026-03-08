package com.surver.sys.houduan.module.user.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.log.service.LogService;
import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserRoleRequest;
import com.surver.sys.houduan.module.user.dto.UserStatusRequest;
import com.surver.sys.houduan.module.user.service.UserService;
import com.surver.sys.houduan.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final LogService logService;

    public UserController(UserService userService, LogService logService) {
        this.userService = userService;
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<?> listUsers() {
        return ApiResponse.success(userService.listUsers());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> createUser(@Valid @RequestBody CreateUserRequest request) {
        userService.createUser(request);
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "CREATE", request.username());
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUser(id, request);
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE", "用户ID=" + id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "DELETE", "用户ID=" + id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UserRoleRequest request) {
        userService.updateRole(id, request.role());
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE", "角色变更 用户ID=" + id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request.status());
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE", "状态变更 用户ID=" + id);
        return ApiResponse.success();
    }
}
