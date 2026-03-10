package com.surver.sys.houduan.module.settings.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.log.service.LogServiceApi;
import com.surver.sys.houduan.module.settings.service.SettingsServiceApi;
import com.surver.sys.houduan.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsServiceApi settingsService;
    private final LogServiceApi logService;

    public SettingsController(SettingsServiceApi settingsService, LogServiceApi logService) {
        this.settingsService = settingsService;
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Map<String, Object>> getSettings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @GetMapping("/public")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getPublicBrandingSettings() {
        Map<String, Object> settings = settingsService.getSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemName", settings.getOrDefault("systemName", ""));
        result.put("adminLogo", settings.getOrDefault("adminLogo", ""));
        result.put("userHomeLogo", settings.getOrDefault("userHomeLogo", ""));
        return ApiResponse.success(result);
    }

    @GetMapping("/public-auth")
    public ApiResponse<Map<String, Object>> getPublicAuthSettings() {
        Map<String, Object> settings = settingsService.getSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authIntegration", sanitizeAuthIntegration(settings.get("authIntegration")));
        return ApiResponse.success(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> saveSettings(@RequestBody Map<String, Object> settings) {
        settingsService.saveSettings(settings);
        String operator = SecurityUtils.getCurrentUser().username();
        logService.appendSystemLog(operator, "SYSTEM", "UPDATE", "系统设置更新");
        return ApiResponse.success();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeAuthIntegration(Object rawIntegration) {
        Map<String, Object> integration =
                rawIntegration instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loginMode", integration.getOrDefault("loginMode", "LOCAL_ONLY"));
        result.put("defaultProviderId", integration.getOrDefault("defaultProviderId", ""));
        result.put("autoCreateUser", integration.getOrDefault("autoCreateUser", true));
        result.put("defaultRole", integration.getOrDefault("defaultRole", "ROLE1"));

        List<Map<String, Object>> providers = new ArrayList<>();
        Object rawProviders = integration.get("providers");
        if (rawProviders instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> providerMap)) {
                    continue;
                }
                Map<String, Object> provider = new LinkedHashMap<>((Map<String, Object>) providerMap);
                provider.putIfAbsent("id", "");
                provider.putIfAbsent("name", "");
                provider.putIfAbsent("protocol", "IAM_TEMPLATE");
                provider.putIfAbsent("enabled", false);
                provider.putIfAbsent("priority", providers.size() + 1);
                provider.putIfAbsent("environment", "CUSTOM");
                provider.putIfAbsent("authDomain", "");
                provider.putIfAbsent("clientId", "");
                provider.put("clientSecret", "");
                provider.putIfAbsent("scope", "");
                provider.putIfAbsent("redirectUri", "");
                provider.putIfAbsent("logoutRedirectUri", "");
                provider.putIfAbsent("authorizeUrl", "");
                provider.putIfAbsent("tokenUrl", "");
                provider.putIfAbsent("userInfoUrl", "");
                provider.putIfAbsent("refreshUrl", "");
                provider.putIfAbsent("revokeUrl", "");
                provider.putIfAbsent("userIdField", "employeeNumber");
                provider.putIfAbsent("realNameField", "displayName");
                provider.putIfAbsent("emailField", "mail");
                providers.add(provider);
            }
        }
        result.put("providers", providers);
        return result;
    }
}
