package com.surver.sys.houduan.module.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SurveyAuthUserDto(
        @NotNull(message = "userId 不能为空") Long userId,
        @NotBlank(message = "username 不能为空") String username,
        @NotBlank(message = "realName 不能为空") String realName
) {
}
