package com.surver.sys.houduan.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthCallbackRequest(
        @NotBlank(message = "providerId 不能为空") String providerId,
        @NotBlank(message = "code 不能为空") String code,
        @NotBlank(message = "state 不能为空") String state,
        String redirectPath
) {
}
