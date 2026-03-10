package com.surver.sys.houduan.module.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CreateSurveyRequest(
        @NotBlank(message = "title cannot be blank") String title,
        String description,
        @NotNull(message = "questions cannot be null") List<Map<String, Object>> questions,
        Boolean allowDuplicateSubmit
) {
}
