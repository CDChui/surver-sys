package com.surver.sys.houduan.module.auth.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.auth.dto.LocalLoginRequest;
import com.surver.sys.houduan.module.auth.dto.LoginResponse;
import com.surver.sys.houduan.module.auth.dto.OauthCallbackRequest;
import com.surver.sys.houduan.module.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/local/login")
    public ApiResponse<LoginResponse> localLogin(@Valid @RequestBody LocalLoginRequest request) {
        return ApiResponse.success(authService.localLogin(request));
    }

    @PostMapping("/oauth/callback")
    public ApiResponse<LoginResponse> oauthCallback(@Valid @RequestBody OauthCallbackRequest request) {
        return ApiResponse.success(authService.oauthCallback(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        authService.logout(token);
        return ApiResponse.success();
    }
}
