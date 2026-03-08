package com.surver.sys.houduan.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalLoginRequest(
        @NotBlank(message = "username 不能为空") String username,
        @NotBlank(message = "password 不能为空") String password
) {
}
