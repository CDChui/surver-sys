package com.surver.sys.houduan.security;

public record UserPrincipal(
        Long userId,
        String uid,
        String username,
        String displayName,
        String role,
        String jti
) {
}
