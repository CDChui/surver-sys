package com.surver.sys.houduan.module.survey.dto;

import java.util.List;
import java.util.Map;

public record SurveyDetailResponse(
        Long id,
        String title,
        String description,
        String status,
        List<Map<String, Object>> schema,
        Long creatorId
) {
}
