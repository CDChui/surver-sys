package com.surver.sys.houduan.module.log.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLogRequest(
        @NotBlank(message = "operator 不能为空") String operator,
        @NotBlank(message = "module 不能为空") String module,
        @NotBlank(message = "action 不能为空") String action,
        @NotBlank(message = "target 不能为空") String target,
        String createdAt
) {
}
