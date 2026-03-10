package com.surver.sys.houduan.module.survey.dto;

import java.util.List;
import java.util.Map;

public record MySurveySubmissionDetailResponse(
        Long surveyId,
        String surveyTitle,
        String surveyDescription,
        List<Map<String, Object>> schema,
        Map<String, Object> answers,
        String submitTime
) {
}

