package com.surver.sys.houduan.module.auth.dto;

public record LoginResponse(
        String token,
        String role,
        String username,
        String realName,
        Long userId,
        boolean localAccount
) {
}
