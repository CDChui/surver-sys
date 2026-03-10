package com.surver.sys.houduan.module.survey.controller;

import com.surver.sys.houduan.common.ApiResponse;
import com.surver.sys.houduan.module.survey.dto.CreateSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionDetailResponse;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionItemResponse;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyAuthUserDto;
import com.surver.sys.houduan.module.survey.dto.SurveyDetailResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyListItemResponse;
import com.surver.sys.houduan.module.survey.dto.UpdateSurveyRequest;
import com.surver.sys.houduan.module.survey.service.SurveyServiceApi;
import com.surver.sys.houduan.security.SecurityUtils;
import com.surver.sys.houduan.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyServiceApi surveyService;

    public SurveyController(SurveyServiceApi surveyService) {
        this.surveyService = surveyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<SurveyDetailResponse> createSurvey(@Valid @RequestBody CreateSurveyRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.createSurvey(principal, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<List<SurveyListItemResponse>> getSurveyList() {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.listSurveys(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<SurveyDetailResponse> getSurveyDetail(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.getSurveyDetail(principal, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<SurveyDetailResponse> updateSurvey(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateSurveyRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.updateSurvey(principal, id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<Void> publishSurvey(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        surveyService.publishSurvey(principal, id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<Void> closeSurvey(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        surveyService.closeSurvey(principal, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<Void> deleteSurvey(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        surveyService.deleteSurvey(principal, id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<?> getSurveyStats(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.getSurveyStats(principal, id));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ResponseEntity<byte[]> exportSurvey(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        SurveyDetailResponse surveyDetail = surveyService.getSurveyDetail(principal, id);
        byte[] content = surveyService.exportSurveyData(principal, id);
        String exportFileName = buildExportFileName(id, surveyDetail.title());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportFileName, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private static String buildExportFileName(Long surveyId, String surveyTitle) {
        String normalizedTitle = surveyTitle == null ? "" : surveyTitle.trim();
        normalizedTitle = normalizedTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (normalizedTitle.isBlank()) {
            normalizedTitle = "问卷";
        }
        return surveyId + normalizedTitle + ".xlsx";
    }

    @GetMapping("/{id}/auth")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<List<SurveyAuthUserDto>> getAuthUsers(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.listAuthUsers(principal, id));
    }

    @PostMapping("/{id}/auth")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<Void> addAuthUser(@PathVariable Long id, @Valid @RequestBody SurveyAuthUserDto request) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        surveyService.addAuthUser(principal, id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/auth/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE2','ROLE3')")
    public ApiResponse<Void> removeAuthUser(@PathVariable Long id, @PathVariable Long userId) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        surveyService.removeAuthUser(principal, id, userId);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/public")
    @PreAuthorize("hasAnyAuthority('ROLE1','ROLE2','ROLE3')")
    public ApiResponse<?> getPublicSurvey(@PathVariable Long id,
                                          @RequestParam(name = "previewMode", defaultValue = "false")
                                          boolean previewMode) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.getPublicSurvey(principal, id, previewMode));
    }

    @PostMapping("/{id}/responses")
    @PreAuthorize("hasAnyAuthority('ROLE1','ROLE2','ROLE3')")
    public ApiResponse<SubmitSurveyResponse> submitSurvey(@PathVariable Long id,
                                                          @Valid @RequestBody SubmitSurveyRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.submitSurvey(principal, id, request));
    }

    @GetMapping("/my/submissions")
    @PreAuthorize("hasAnyAuthority('ROLE1','ROLE2','ROLE3')")
    public ApiResponse<List<MySurveySubmissionItemResponse>> listMySubmissions() {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.listMySubmissions(principal));
    }

    @GetMapping("/{id}/my-submission")
    @PreAuthorize("hasAnyAuthority('ROLE1','ROLE2','ROLE3')")
    public ApiResponse<MySurveySubmissionDetailResponse> getMySubmissionDetail(@PathVariable Long id) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        return ApiResponse.success(surveyService.getMySubmissionDetail(principal, id));
    }
}
