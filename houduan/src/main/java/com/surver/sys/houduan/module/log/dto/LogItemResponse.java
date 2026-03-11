package com.surver.sys.houduan.module.log.dto;

public record LogItemResponse(
        Long id,
        String operator,
        String module,
        String action,
        String target,
        String createdAt,
        String terminalType,
        String sourceIp
) {
}
