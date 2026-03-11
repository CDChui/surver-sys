package com.surver.sys.houduan.module.log.service;

import com.surver.sys.houduan.module.log.dto.CreateLogRequest;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;

import java.util.List;

public interface LogServiceApi {

    List<LogItemResponse> listLogs(String logType, String order);

    void createLog(CreateLogRequest request);

    void appendSystemLog(String operator, String module, String action, String target);
}
