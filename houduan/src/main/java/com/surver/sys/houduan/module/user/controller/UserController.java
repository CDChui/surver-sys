package com.surver.sys.houduan.module.user.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.log.service.LogServiceApi;
import com.surver.sys.houduan.module.user.dto.ChangeOwnPasswordRequest;
import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.ResetUserPasswordRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserRoleRequest;
import com.surver.sys.houduan.module.user.dto.UserStatusRequest;
import com.surver.sys.houduan.module.user.service.UserServiceApi;
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

    private final UserServiceApi userService;
    private final LogServiceApi logService;

    public UserController(UserServiceApi userService, LogServiceApi logService) {
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
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "CREATE",
                "用户账号-" + request.username());
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUser(id, request);
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE",
                "用户ID-" + id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "DELETE",
                "用户ID-" + id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UserRoleRequest request) {
        userService.updateRole(id, request.role());
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE",
                "用户ID-" + id + " 角色变更");
        return ApiResponse.success();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request.status());
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE",
                "用户ID-" + id + " 状态变更");
        return ApiResponse.success();
    }

    @PostMapping("/{id}/password/reset")
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> resetUserPassword(@PathVariable Long id,
                                               @Valid @RequestBody ResetUserPasswordRequest request) {
        userService.resetLocalUserPassword(id, request.newPassword());
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE",
                "用户ID-" + id + " 重置密码");
        return ApiResponse.success();
    }

    @PostMapping("/password/change")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changeOwnPassword(@Valid @RequestBody ChangeOwnPasswordRequest request) {
        userService.changeOwnLocalPassword(
                SecurityUtils.getCurrentUser().userId(),
                request.oldPassword(),
                request.newPassword()
        );
        logService.appendSystemLog(SecurityUtils.getCurrentUser().username(), "USER", "UPDATE",
                "修改本人密码");
        return ApiResponse.success();
    }
}
