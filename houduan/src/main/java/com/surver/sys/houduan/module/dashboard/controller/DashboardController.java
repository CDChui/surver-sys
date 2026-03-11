package com.surver.sys.houduan.module.dashboard.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.dashboard.dto.DashboardResponse;
import com.surver.sys.houduan.module.dashboard.service.DashboardServiceApi;
import com.surver.sys.houduan.security.SecurityUtils;
import com.surver.sys.houduan.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardServiceApi dashboardService;

    public DashboardController(DashboardServiceApi dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<DashboardResponse> getDashboard() {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(dashboardService.getDashboard(principal));
    }
}
