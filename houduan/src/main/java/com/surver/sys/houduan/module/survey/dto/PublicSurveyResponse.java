package com.surver.sys.houduan.module.survey.dto;

import java.util.List;
import java.util.Map;

public record PublicSurveyResponse(
        Long id,
        String title,
        String description,
        List<Map<String, Object>> schema,
        String entryToken
) {
}
