package com.surver.sys.houduan.module.survey.model;

import com.surver.sys.houduan.module.survey.dto.SurveyAuthUserDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SurveyModel {

    private Long id;
    private String title;
    private String description;
    private String status;
    private Long creatorId;
    private String createdAt;
    private boolean quotaEnabled;
    private Integer quotaTotal;
    private List<Map<String, Object>> schema = new ArrayList<>();
    private final List<SurveyAuthUserDto> authUsers = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isQuotaEnabled() {
        return quotaEnabled;
    }

    public void setQuotaEnabled(boolean quotaEnabled) {
        this.quotaEnabled = quotaEnabled;
    }

    public Integer getQuotaTotal() {
        return quotaTotal;
    }

    public void setQuotaTotal(Integer quotaTotal) {
        this.quotaTotal = quotaTotal;
    }

    public List<Map<String, Object>> getSchema() {
        return schema;
    }

    public void setSchema(List<Map<String, Object>> schema) {
        this.schema = schema;
    }

    public List<SurveyAuthUserDto> getAuthUsers() {
        return authUsers;
    }
}
