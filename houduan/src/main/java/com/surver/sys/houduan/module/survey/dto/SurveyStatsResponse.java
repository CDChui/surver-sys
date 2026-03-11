package com.surver.sys.houduan.module.survey.dto;

import java.util.List;
import java.util.Map;

public record SurveyStatsResponse(
        Long id,
        String title,
        String description,
        Long responseCount,
        List<Map<String, Object>> schema,
        List<Map<String, Object>> statsList
) {
}
