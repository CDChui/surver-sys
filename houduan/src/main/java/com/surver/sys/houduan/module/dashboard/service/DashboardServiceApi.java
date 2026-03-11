package com.surver.sys.houduan.module.dashboard.service;

import com.surver.sys.houduan.module.dashboard.dto.DashboardResponse;
import com.surver.sys.houduan.security.UserPrincipal;

public interface DashboardServiceApi {

    DashboardResponse getDashboard(UserPrincipal principal);
}
