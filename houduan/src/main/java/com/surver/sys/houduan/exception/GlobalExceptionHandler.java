package com.surver.sys.houduan.exception;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.failure(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleValidationException(Exception e) {
        if (e instanceof MethodArgumentNotValidException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            return ApiResponse.failure(ErrorCode.INVALID_PARAM, fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage());
        }
        if (e instanceof BindException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            return ApiResponse.failure(ErrorCode.INVALID_PARAM, fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage());
        }
        return ApiResponse.failure(ErrorCode.INVALID_PARAM, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        return ApiResponse.failure(ErrorCode.SERVER_ERROR, e.getMessage());
    }
}
