package com.surver.sys.houduan.common;

public enum ErrorCode {
    SUCCESS(20000, "success"),
    INVALID_PARAM(40001, "请求参数错误"),
    DUPLICATE_SUBMIT(40009, "重复提交"),
    QUOTA_FULL(40011, "名额已满"),
    UNAUTHORIZED(40101, "未登录或登录无效"),
    TOKEN_EXPIRED_OR_REVOKED(40102, "登录已过期"),
    FORBIDDEN(40301, "无权限"),
    NOT_FOUND(40404, "资源不存在"),
    SERVER_ERROR(50000, "服务器异常");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
