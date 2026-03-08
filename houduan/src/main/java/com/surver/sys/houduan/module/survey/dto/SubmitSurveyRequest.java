package com.surver.sys.houduan.module.survey.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitSurveyRequest(
        @NotNull(message = "surveyId 不能为空") Long surveyId,
        @NotNull(message = "answers 不能为空") Map<String, Object> answers,
        String entryToken
) {
}
