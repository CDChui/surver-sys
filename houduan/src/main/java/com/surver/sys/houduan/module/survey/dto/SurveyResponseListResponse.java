package com.surver.sys.houduan.module.survey.dto;

import java.util.List;
import java.util.Map;

public record SurveyResponseListResponse(
        Long surveyId,
        String surveyTitle,
        String surveyDescription,
        List<Map<String, Object>> schema,
        List<SurveyResponseItemResponse> responses
) {
}

