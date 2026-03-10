package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "username must not be blank") String username,
        @NotBlank(message = "realName must not be blank") String realName,
        @Size(max = 255, message = "remark length must be <= 255") String remark,
        @NotBlank(message = "role must not be blank") String role,
        @NotBlank(message = "status must not be blank") String status,
        @NotBlank(message = "initialPassword must not be blank")
        @Size(min = 6, max = 64, message = "initialPassword length must be between 6 and 64") String initialPassword
) {
}
