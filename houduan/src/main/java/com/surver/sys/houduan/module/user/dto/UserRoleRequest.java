package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRoleRequest(
        @NotBlank(message = "role 不能为空") String role
) {
}
