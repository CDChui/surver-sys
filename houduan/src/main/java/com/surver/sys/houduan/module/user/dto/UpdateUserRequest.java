package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "realName must not be blank") String realName,
        @Size(max = 255, message = "remark length must be <= 255") String remark,
        @NotBlank(message = "role must not be blank") String role,
        @NotBlank(message = "status must not be blank") String status
) {
}