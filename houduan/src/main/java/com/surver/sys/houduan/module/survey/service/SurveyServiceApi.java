package com.surver.sys.houduan.module.survey.service;

import com.surver.sys.houduan.module.survey.dto.CreateSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionDetailResponse;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionItemResponse;
import com.surver.sys.houduan.module.survey.dto.PublicSurveyResponse;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyAuthUserDto;
import com.surver.sys.houduan.module.survey.dto.SurveyDetailResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyListItemResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyResponseListResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyStatsResponse;
import com.surver.sys.houduan.module.survey.dto.UpdateSurveyRequest;
import com.surver.sys.houduan.security.UserPrincipal;

import java.util.List;

public interface SurveyServiceApi {

    SurveyDetailResponse createSurvey(UserPrincipal principal, CreateSurveyRequest request);

    List<SurveyListItemResponse> listSurveys(UserPrincipal principal);

    SurveyDetailResponse getSurveyDetail(UserPrincipal principal, Long id);

    SurveyDetailResponse updateSurvey(UserPrincipal principal, Long id, UpdateSurveyRequest request);

    void publishSurvey(UserPrincipal principal, Long id);

    void closeSurvey(UserPrincipal principal, Long id);

    void deleteSurvey(UserPrincipal principal, Long id);

    PublicSurveyResponse getPublicSurvey(UserPrincipal principal, Long id, boolean previewMode);

    SubmitSurveyResponse submitSurvey(UserPrincipal principal,
                                      Long id,
                                      SubmitSurveyRequest request,
                                      String sourceIp,
                                      String userAgent);

    List<MySurveySubmissionItemResponse> listMySubmissions(UserPrincipal principal);

    MySurveySubmissionDetailResponse getMySubmissionDetail(UserPrincipal principal, Long id);

    SurveyStatsResponse getSurveyStats(UserPrincipal principal, Long id);

    SurveyResponseListResponse listSurveyResponses(UserPrincipal principal, Long id);

    byte[] exportSurveyData(UserPrincipal principal, Long id);

    List<SurveyAuthUserDto> listAuthUsers(UserPrincipal principal, Long id);

    void addAuthUser(UserPrincipal principal, Long id, SurveyAuthUserDto user);

    void removeAuthUser(UserPrincipal principal, Long id, Long userId);
}
