package com.surver.sys.houduan.module.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CreateSurveyRequest(
        @NotBlank(message = "title 不能为空") String title,
        String description,
        @NotNull(message = "questions 不能为空") List<Map<String, Object>> questions
) {
}
