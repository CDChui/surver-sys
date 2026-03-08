package com.surver.sys.houduan.module.user.dto;

public record UserItemResponse(
        Long id,
        String username,
        String realName,
        String role,
        String status,
        String createdAt
) {
}
