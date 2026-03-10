package com.surver.sys.houduan.module.survey.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitSurveyRequest(
        @NotNull(message = "surveyId cannot be null") Long surveyId,
        @NotNull(message = "answers cannot be null") Map<String, Object> answers,
        String entryToken,
        Boolean previewMode
) {
}
