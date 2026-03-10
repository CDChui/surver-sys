package com.surver.sys.houduan.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank(message = "oldPassword must not be blank") String oldPassword,
        @NotBlank(message = "newPassword must not be blank")
        @Size(min = 6, max = 64, message = "newPassword length must be between 6 and 64") String newPassword
) {
}
