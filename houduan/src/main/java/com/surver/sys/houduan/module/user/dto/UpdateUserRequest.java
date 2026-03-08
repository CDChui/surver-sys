package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank(message = "realName 不能为空") String realName,
        @NotBlank(message = "role 不能为空") String role,
        @NotBlank(message = "status 不能为空") String status
) {
}
