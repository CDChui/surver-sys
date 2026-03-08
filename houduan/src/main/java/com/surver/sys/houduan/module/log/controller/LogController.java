package com.surver.sys.houduan.module.log.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import com.surver.sys.houduan.module.log.service.LogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<List<LogItemResponse>> listLogs() {
        return ApiResponse.success(logService.listLogs());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE1','ROLE2','ROLE3')")
    public ApiResponse<Void> createLog(@Valid @RequestBody CreateLogRequest request) {
        logService.createLog(request);
        return ApiResponse.success();
    }
}
