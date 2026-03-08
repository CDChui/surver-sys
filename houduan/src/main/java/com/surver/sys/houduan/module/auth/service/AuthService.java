package com.surver.sys.houduan.module.auth.service;

import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.config.SsoMockProperties;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.auth.dto.LocalLoginRequest;
import com.surver.sys.houduan.module.auth.dto.LoginResponse;
import com.surver.sys.houduan.module.auth.dto.OauthCallbackRequest;
import com.surver.sys.houduan.module.log.service.LogService;
import com.surver.sys.houduan.module.user.model.UserModel;
import com.surver.sys.houduan.module.user.service.UserService;
import com.surver.sys.houduan.security.JwtTokenService;
import com.surver.sys.houduan.security.TokenBlacklistService;
import com.surver.sys.houduan.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SsoMockProperties ssoMockProperties;
    private final LogService logService;

    public AuthService(UserService userService,
                       JwtTokenService jwtTokenService,
                       TokenBlacklistService tokenBlacklistService,
                       SsoMockProperties ssoMockProperties,
                       LogService logService) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.ssoMockProperties = ssoMockProperties;
        this.logService = logService;
    }

    public LoginResponse localLogin(LocalLoginRequest request) {
        if (!userService.verifyLocalPassword(request.username(), request.password())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户名或密码错误");
        }
        UserModel user = userService.findLocalUserByUsername(request.username())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        String token = jwtTokenService.generateToken(toPrincipal(user));
        logService.appendSystemLog(user.getUsername(), "SYSTEM", "LOGIN", "后台本地登录");
        return new LoginResponse(token, user.getRole(), user.getUsername(), user.getRealName(), user.getId());
    }

    public LoginResponse oauthCallback(OauthCallbackRequest request) {
        if (!ssoMockProperties.isEnabled()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "当前环境未开启 SSO Mock，真实 IAM 对接未实现");
        }
        String redirect = request.redirectPath() == null ? "" : request.redirectPath().trim();
        String role = redirect.startsWith("/admin") ? "ROLE3" : "ROLE1";
        String username = role.equals("ROLE3") ? "oauth_admin" : "oauth_user";
        String realName = role.equals("ROLE3") ? "第三方管理员" : "第三方用户";
        UserModel user = userService.findOrCreateOauthUser(username, realName, role);
        String token = jwtTokenService.generateToken(new UserPrincipal(
                user.getId(),
                UUID.randomUUID().toString().replace("-", ""),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                UUID.randomUUID().toString()
        ));
        logService.appendSystemLog(user.getUsername(), "SYSTEM", "LOGIN", "第三方登录: " + request.providerId());
        return new LoginResponse(token, user.getRole(), user.getUsername(), user.getRealName(), user.getId());
    }

    public void logout(String token) {
        UserPrincipal principal = jwtTokenService.parse(token);
        long remainSeconds = jwtTokenService.getRemainingSeconds(token);
        tokenBlacklistService.blacklist(principal.jti(), remainSeconds);
        logService.appendSystemLog(principal.username(), "SYSTEM", "LOGOUT", "用户退出登录");
    }

    private static UserPrincipal toPrincipal(UserModel user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                UUID.randomUUID().toString()
        );
    }
}
