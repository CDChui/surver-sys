package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserStatusRequest(
        @NotBlank(message = "status 不能为空") String status
) {
}
