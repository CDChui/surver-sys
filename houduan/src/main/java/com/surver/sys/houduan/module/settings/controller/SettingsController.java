package com.surver.sys.houduan.module.settings.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.log.service.LogService;
import com.surver.sys.houduan.module.settings.service.SettingsService;
import com.surver.sys.houduan.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final LogService logService;

    public SettingsController(SettingsService settingsService, LogService logService) {
        this.settingsService = settingsService;
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Map<String, Object>> getSettings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE3')")
    public ApiResponse<Void> saveSettings(@RequestBody Map<String, Object> settings) {
        settingsService.saveSettings(settings);
        String operator = SecurityUtils.getCurrentUser().username();
        logService.appendSystemLog(operator, "SYSTEM", "UPDATE", "系统设置更新");
        return ApiResponse.success();
    }
}
