package com.surver.sys.houduan.module.survey.dto;

import java.util.Map;

public record SurveyResponseItemResponse(
        Long userId,
        String account,
        String username,
        String submitTime,
        String terminalType,
        String sourceType,
        String sourceIp,
        Map<String, Object> answers
) {
}
