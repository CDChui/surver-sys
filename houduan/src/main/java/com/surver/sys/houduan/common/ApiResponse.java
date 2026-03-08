package com.surver.sys.houduan.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(), data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(), null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        String finalMessage = (message == null || message.isBlank()) ? errorCode.getDefaultMessage() : message;
        return new ApiResponse<>(errorCode.getCode(), finalMessage, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.getDefaultMessage());
    }
}
