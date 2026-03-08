package com.surver.sys.houduan.module.survey.dto;

public record SurveyListItemResponse(
        Long id,
        String title,
        String status,
        String createdAt,
        Long creatorId
) {
}
