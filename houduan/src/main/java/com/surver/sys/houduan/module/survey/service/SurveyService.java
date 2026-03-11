package com.surver.sys.houduan.module.survey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.exception.BizException;
import com.surver.sys.houduan.module.log.service.LogServiceApi;
import com.surver.sys.houduan.module.survey.dto.CreateSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionDetailResponse;
import com.surver.sys.houduan.module.survey.dto.MySurveySubmissionItemResponse;
import com.surver.sys.houduan.module.survey.dto.PublicSurveyResponse;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyRequest;
import com.surver.sys.houduan.module.survey.dto.SubmitSurveyResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyAuthUserDto;
import com.surver.sys.houduan.module.survey.dto.SurveyDetailResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyListItemResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyResponseItemResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyResponseListResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyStatsResponse;
import com.surver.sys.houduan.module.survey.dto.UpdateSurveyRequest;
import com.surver.sys.houduan.module.survey.model.SurveyModel;
import com.surver.sys.houduan.security.UserPrincipal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Profile("!nodeps")
public class SurveyService implements SurveyServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration ENTRY_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofSeconds(10);
    private static final int TEXT_SAMPLE_LIMIT = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SurveyRedisService surveyRedisService;
    private final LogServiceApi logService;

    public SurveyService(JdbcTemplate jdbcTemplate,
                         ObjectMapper objectMapper,
                         SurveyRedisService surveyRedisService,
                         LogServiceApi logService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.surveyRedisService = surveyRedisService;
        this.logService = logService;
    }

    public SurveyDetailResponse createSurvey(UserPrincipal principal, CreateSurveyRequest request) {
        String normalizedTitle = SurveyTitleCodec.normalizeInputTitle(request.title());
        if (SurveyTitleCodec.isLikelyBrokenTitle(normalizedTitle)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "问卷标题编码异常，请使用 UTF-8 客户端提交");
        }
        String schemaJson = writeJson(cloneSchema(request.questions()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        boolean allowDuplicateSubmit = boolFlag(request.allowDuplicateSubmit());
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO survey_info (
                        title, description, status, creator_user_id,
                        schema_data, allow_duplicate_submit, quota_enabled, quota_total
                    )
                    VALUES (?, ?, 'DRAFT', ?, CAST(? AS JSON), ?, 0, NULL)
                    """, new String[]{"id"});
            ps.setString(1, normalizedTitle);
            ps.setString(2, request.description() == null ? "" : request.description().trim());
            ps.setLong(3, principal.userId());
            ps.setString(4, schemaJson);
            ps.setInt(5, allowDuplicateSubmit ? 1 : 0);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SERVER_ERROR, "创建问卷失败");
        }
        SurveyDetailResponse created = toDetail(getSurvey(key.longValue()));
        logService.appendSystemLog(principal.username(), "SURVEY", "CREATE",
                formatSurveyTarget(created.id(), created.title()));
        return created;
    }

    public List<SurveyListItemResponse> listSurveys(UserPrincipal principal) {
        if ("ROLE1".equals(principal.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问问卷列表");
        }
        if ("ROLE3".equals(principal.role())) {
            return jdbcTemplate.query("""
                    SELECT id, title, status, created_at, creator_user_id
                    FROM survey_info
                    WHERE status <> 'DELETED'
                    ORDER BY created_at DESC, id DESC
                    """, (rs, rowNum) -> new SurveyListItemResponse(
                    rs.getLong("id"),
                    SurveyTitleCodec.repairLegacyTitle(rs.getString("title"), rs.getLong("id")),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER),
                    rs.getLong("creator_user_id")
            ));
        }
        return jdbcTemplate.query("""
                SELECT DISTINCT s.id, s.title, s.status, s.created_at, s.creator_user_id
                FROM survey_info s
                LEFT JOIN survey_auth a ON a.survey_id = s.id
                WHERE s.status <> 'DELETED'
                  AND (s.creator_user_id = ? OR a.grantee_user_id = ?)
                ORDER BY s.created_at DESC, s.id DESC
                """, (rs, rowNum) -> new SurveyListItemResponse(
                rs.getLong("id"),
                SurveyTitleCodec.repairLegacyTitle(rs.getString("title"), rs.getLong("id")),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER),
                rs.getLong("creator_user_id")
        ), principal.userId(), principal.userId());
    }

    public SurveyDetailResponse getSurveyDetail(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        if (!canAccessSurvey(principal, model.getId(), model.getCreatorId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问问卷详情");
        }
        return toDetail(model);
    }

    public SurveyDetailResponse updateSurvey(UserPrincipal principal, Long id, UpdateSurveyRequest request) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        String normalizedTitle = SurveyTitleCodec.normalizeInputTitle(request.title());
        if (SurveyTitleCodec.isLikelyBrokenTitle(normalizedTitle)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "问卷标题编码异常，请使用 UTF-8 客户端提交");
        }
        jdbcTemplate.update("""
                UPDATE survey_info
                SET title = ?, description = ?, schema_data = CAST(? AS JSON), allow_duplicate_submit = ?
                WHERE id = ?
                """,
                normalizedTitle,
                request.description() == null ? "" : request.description().trim(),
                writeJson(cloneSchema(request.questions())),
                boolFlag(request.allowDuplicateSubmit()) ? 1 : 0,
                id
        );
        SurveyDetailResponse updated = toDetail(getSurvey(id));
        logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE",
                formatSurveyTarget(updated.id(), updated.title()));
        return updated;
    }

    public void publishSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        jdbcTemplate.update("UPDATE survey_info SET status = 'PUBLISHED' WHERE id = ?", id);
        if (hasQuota(model)) {
            surveyRedisService.resetQuotaCount(id, countAnswers(id));
        }
        logService.appendSystemLog(principal.username(), "SURVEY", "PUBLISH",
                formatSurveyTarget(model.getId(), model.getTitle()));
    }

    public void closeSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        jdbcTemplate.update("UPDATE survey_info SET status = 'CLOSED' WHERE id = ?", id);
        logService.appendSystemLog(principal.username(), "SURVEY", "CLOSE",
                formatSurveyTarget(model.getId(), model.getTitle()));
    }

    public void deleteSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        jdbcTemplate.update("UPDATE survey_info SET status = 'DELETED', deleted_at = NOW() WHERE id = ?", id);
        logService.appendSystemLog(principal.username(), "SURVEY", "DELETE",
                formatSurveyTarget(model.getId(), model.getTitle()));
    }

    public PublicSurveyResponse getPublicSurvey(UserPrincipal principal, Long id, boolean previewMode) {
        SurveyModel model = getSurvey(id);
        ensureSurveyReadableByPrincipal(principal, model);

        boolean allowDuplicateSubmit = model.isAllowDuplicateSubmit();
        boolean submitted = hasSubmitted(id, principal.userId());
        if (!previewMode && !allowDuplicateSubmit && submitted) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "已经提交过该问卷");
        }

        String entryToken = null;
        if (!previewMode && hasQuota(model)) {
            long quotaCount = surveyRedisService.getOrInitQuotaCount(id, () -> countAnswers(id));
            boolean hasTicket = surveyRedisService.hasEntryToken(id, principal.userId());
            if (quotaCount >= model.getQuotaTotal() && !hasTicket && !submitted) {
                throw new BizException(ErrorCode.QUOTA_FULL, "问卷名额已满");
            }
            entryToken = surveyRedisService.issueEntryToken(id, principal.userId(), ENTRY_TOKEN_TTL);
        }

        return new PublicSurveyResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                cloneSchema(model.getSchema()),
                entryToken
        );
    }

    public SubmitSurveyResponse submitSurvey(UserPrincipal principal,
                                             Long id,
                                             SubmitSurveyRequest request,
                                             String sourceIp,
                                             String userAgent) {
        if (!Objects.equals(id, request.surveyId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "问卷ID不匹配");
        }
        SurveyModel model = getSurvey(id);
        ensureSurveyReadableByPrincipal(principal, model);
        boolean previewMode = boolFlag(request.previewMode());
        String safeIp = normalizeIp(sourceIp);
        String safeUserAgent = normalizeUserAgent(userAgent);

        boolean allowDuplicateSubmit = model.isAllowDuplicateSubmit();
        boolean submittedBefore = hasSubmitted(id, principal.userId());
        if (!previewMode && !allowDuplicateSubmit && submittedBefore) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "已经提交过该问卷");
        }

        boolean locked = surveyRedisService.tryAcquireSubmitLock(id, principal.userId(), SUBMIT_LOCK_TTL);
        if (!locked) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "提交过于频繁，请稍后再试");
        }

        try {
            if (!previewMode && hasQuota(model) && !submittedBefore) {
                String storedToken = surveyRedisService.getEntryToken(id, principal.userId());
                if (storedToken == null || storedToken.isBlank()) {
                    throw new BizException(ErrorCode.QUOTA_FULL, "入场凭证缺失，请刷新后重试");
                }
                String requestToken = request.entryToken();
                if (requestToken != null && !requestToken.isBlank() && !Objects.equals(requestToken, storedToken)) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "入场凭证无效");
                }
                long quotaCount = surveyRedisService.getOrInitQuotaCount(id, () -> countAnswers(id));
                if (quotaCount >= model.getQuotaTotal()) {
                    throw new BizException(ErrorCode.QUOTA_FULL, "问卷名额已满");
                }
            }

            if (previewMode) {
                logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE",
                        "预览提交 " + formatSurveyTarget(id, model.getTitle()));
                return new SubmitSurveyResponse(id, nowText());
            }

            String answerJson = writeJson(request.answers());
            String schemaSnapshotJson = writeJson(cloneSchema(model.getSchema()));
            String titleSnapshot = model.getTitle();
            String descriptionSnapshot = model.getDescription();
            boolean inserted = false;

            if (allowDuplicateSubmit) {
                if (submittedBefore) {
                    jdbcTemplate.update("""
                            UPDATE survey_answer
                            SET answer_data = CAST(? AS JSON),
                                survey_title_snapshot = ?,
                                survey_description_snapshot = ?,
                                survey_schema_snapshot = CAST(? AS JSON),
                                submit_time = NOW(),
                                ip = ?,
                                user_agent = ?
                            WHERE survey_id = ? AND user_id = ?
                            """, answerJson, titleSnapshot, descriptionSnapshot, schemaSnapshotJson, safeIp, safeUserAgent, id, principal.userId());
                } else {
                    jdbcTemplate.update("""
                            INSERT INTO survey_answer (
                                survey_id, user_id, answer_data, survey_title_snapshot, survey_description_snapshot, survey_schema_snapshot, ip, user_agent
                            )
                            VALUES (?, ?, CAST(? AS JSON), ?, ?, CAST(? AS JSON), ?, ?)
                            """, id, principal.userId(), answerJson, titleSnapshot, descriptionSnapshot, schemaSnapshotJson, safeIp, safeUserAgent);
                    inserted = true;
                }
            } else {
                try {
                    jdbcTemplate.update("""
                            INSERT INTO survey_answer (
                                survey_id, user_id, answer_data, survey_title_snapshot, survey_description_snapshot, survey_schema_snapshot, ip, user_agent
                            )
                            VALUES (?, ?, CAST(? AS JSON), ?, ?, CAST(? AS JSON), ?, ?)
                            """, id, principal.userId(), answerJson, titleSnapshot, descriptionSnapshot, schemaSnapshotJson, safeIp, safeUserAgent);
                    inserted = true;
                } catch (DataIntegrityViolationException e) {
                    throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "已经提交过该问卷");
                }
            }

            if (hasQuota(model) && inserted) {
                surveyRedisService.incrementQuotaCount(id);
                surveyRedisService.clearEntryToken(id, principal.userId());
            }

            logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE",
                    "提交答卷 " + formatSurveyTarget(id, model.getTitle()));
            return new SubmitSurveyResponse(id, nowText());
        } finally {
            surveyRedisService.releaseSubmitLock(id, principal.userId());
        }
    }

    @Override
    public List<MySurveySubmissionItemResponse> listMySubmissions(UserPrincipal principal) {
        return jdbcTemplate.query("""
                SELECT survey_id, survey_title_snapshot, submit_time
                FROM survey_answer
                WHERE user_id = ?
                ORDER BY submit_time DESC
                """, (rs, rowNum) -> new MySurveySubmissionItemResponse(
                rs.getLong("survey_id"),
                SurveyTitleCodec.repairLegacyTitle(rs.getString("survey_title_snapshot"), rs.getLong("survey_id")),
                rs.getTimestamp("submit_time").toLocalDateTime().format(DATE_TIME_FORMATTER)
        ), principal.userId());
    }

    @Override
    public MySurveySubmissionDetailResponse getMySubmissionDetail(UserPrincipal principal, Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT survey_id, survey_title_snapshot, survey_description_snapshot, survey_schema_snapshot, answer_data, submit_time
                    FROM survey_answer
                    WHERE survey_id = ? AND user_id = ?
                    """, (rs, rowNum) -> new MySurveySubmissionDetailResponse(
                    rs.getLong("survey_id"),
                    SurveyTitleCodec.repairLegacyTitle(rs.getString("survey_title_snapshot"), rs.getLong("survey_id")),
                    str(rs.getString("survey_description_snapshot")),
                    parseSchema(rs.getString("survey_schema_snapshot")),
                    parseJsonToMap(rs.getString("answer_data")),
                    rs.getTimestamp("submit_time").toLocalDateTime().format(DATE_TIME_FORMATTER)
            ), id, principal.userId());
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "答卷不存在");
        }
    }

    public SurveyStatsResponse getSurveyStats(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        List<Map<String, Object>> statsList = buildStats(id, model.getSchema());
        return new SurveyStatsResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                countAnswers(id),
                cloneSchema(model.getSchema()),
                statsList
        );
    }

    @Override
    public SurveyResponseListResponse listSurveyResponses(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());

        List<SurveyResponseItemResponse> responses = jdbcTemplate.query("""
                SELECT a.user_id, a.submit_time, a.answer_data, a.ip, a.user_agent, u.uid, u.display_name
                FROM survey_answer a
                JOIN sys_user u ON u.id = a.user_id
                WHERE a.survey_id = ?
                ORDER BY a.submit_time DESC, a.user_id DESC
                """, (rs, rowNum) -> new SurveyResponseItemResponse(
                rs.getLong("user_id"),
                rs.getString("uid"),
                rs.getString("display_name"),
                rs.getTimestamp("submit_time").toLocalDateTime().format(DATE_TIME_FORMATTER),
                resolveTerminalType(rs.getString("user_agent")),
                resolveSourceType(rs.getString("user_agent")),
                str(rs.getString("ip")),
                parseJsonToMap(rs.getString("answer_data"))
        ), id);

        return new SurveyResponseListResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                cloneSchema(model.getSchema()),
                responses
        );
    }

    public byte[] exportSurveyData(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("答卷导出");
            Row header = sheet.createRow(0);
            int col = 0;
            header.createCell(col++).setCellValue("提交时间");
            header.createCell(col++).setCellValue("用户账号");
            header.createCell(col++).setCellValue("用户姓名");
            for (Map<String, Object> question : model.getSchema()) {
                header.createCell(col++).setCellValue(str(question.get("title")));
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT a.submit_time, u.uid, u.display_name, a.answer_data
                    FROM survey_answer a
                    JOIN sys_user u ON u.id = a.user_id
                    WHERE a.survey_id = ?
                    ORDER BY a.submit_time ASC
                    """, id);
            int rowIndex = 1;
            for (Map<String, Object> rowData : rows) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                row.createCell(c++).setCellValue(str(rowData.get("submit_time")));
                row.createCell(c++).setCellValue(str(rowData.get("uid")));
                row.createCell(c++).setCellValue(str(rowData.get("display_name")));
                Map<String, Object> answerMap = parseJsonToMap(rowData.get("answer_data"));
                for (Map<String, Object> question : model.getSchema()) {
                    String qid = String.valueOf(question.get("id"));
                    Object value = answerMap.get(qid);
                    if (value == null) {
                        row.createCell(c++).setCellValue("");
                    } else if (value instanceof List<?> list) {
                        row.createCell(c++).setCellValue(String.join("；", list.stream().map(String::valueOf).toList()));
                    } else {
                        row.createCell(c++).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE",
                    "导出答卷 " + formatSurveyTarget(id, model.getTitle()));
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "导出答卷失败: " + e.getMessage());
        }
    }

    public List<SurveyAuthUserDto> listAuthUsers(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        return jdbcTemplate.query("""
                SELECT u.id AS user_id, u.uid, u.display_name
                FROM survey_auth a
                JOIN sys_user u ON u.id = a.grantee_user_id
                WHERE a.survey_id = ?
                ORDER BY u.id ASC
                """, (rs, rowNum) -> new SurveyAuthUserDto(
                rs.getLong("user_id"),
                rs.getString("uid"),
                rs.getString("display_name")
        ), id);
    }

    public void addAuthUser(UserPrincipal principal, Long id, SurveyAuthUserDto user) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM survey_auth
                WHERE survey_id = ? AND grantee_user_id = ?
                """, Integer.class, id, user.userId());
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO survey_auth (survey_id, grantee_user_id, granted_by)
                VALUES (?, ?, ?)
                """, id, user.userId(), principal.userId());
        logService.appendSystemLog(principal.username(), "PERMISSION", "UPDATE",
                "授权用户ID-" + user.userId() + " " + formatSurveyTarget(id, model.getTitle()));
    }

    public void removeAuthUser(UserPrincipal principal, Long id, Long userId) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        jdbcTemplate.update("""
                DELETE FROM survey_auth
                WHERE survey_id = ? AND grantee_user_id = ?
                """, id, userId);
        logService.appendSystemLog(principal.username(), "PERMISSION", "UPDATE",
                "取消授权用户ID-" + userId + " " + formatSurveyTarget(id, model.getTitle()));
    }

    private void ensureCanManageSurvey(UserPrincipal principal, Long surveyId, Long creatorId) {
        if (!canAccessSurvey(principal, surveyId, creatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限管理该问卷");
        }
    }

    private void ensureSurveyReadableByPrincipal(UserPrincipal principal, SurveyModel model) {
        if ("DELETED".equals(model.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "问卷不存在");
        }
        if (!"PUBLISHED".equals(model.getStatus()) && !isManagerRole(principal)) {
            throw new BizException(ErrorCode.NOT_FOUND, "问卷不存在");
        }
    }

    private static boolean isManagerRole(UserPrincipal principal) {
        return "ROLE2".equals(principal.role()) || "ROLE3".equals(principal.role());
    }

    private boolean canAccessSurvey(UserPrincipal principal, Long surveyId, Long creatorId) {
        if ("ROLE3".equals(principal.role())) {
            return true;
        }
        if ("ROLE2".equals(principal.role())) {
            if (Objects.equals(creatorId, principal.userId())) {
                return true;
            }
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM survey_auth
                    WHERE survey_id = ? AND grantee_user_id = ?
                    """, Integer.class, surveyId, principal.userId());
            return count != null && count > 0;
        }
        return false;
    }

    private SurveyModel getSurvey(Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, title, description, status, creator_user_id, created_at,
                           schema_data, allow_duplicate_submit, quota_enabled, quota_total
                    FROM survey_info
                    WHERE id = ?
                    """, (rs, rowNum) -> {
                SurveyModel model = new SurveyModel();
                model.setId(rs.getLong("id"));
                model.setTitle(SurveyTitleCodec.repairLegacyTitle(rs.getString("title"), rs.getLong("id")));
                model.setDescription(rs.getString("description"));
                model.setStatus(rs.getString("status"));
                model.setCreatorId(rs.getLong("creator_user_id"));
                model.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER));
                model.setAllowDuplicateSubmit(rs.getInt("allow_duplicate_submit") == 1);
                model.setQuotaEnabled(rs.getInt("quota_enabled") == 1);
                Integer quotaTotal = rs.getObject("quota_total", Integer.class);
                model.setQuotaTotal(quotaTotal);
                model.setSchema(parseSchema(rs.getString("schema_data")));
                return model;
            }, id);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "问卷不存在");
        }
    }

    private List<Map<String, Object>> buildStats(Long surveyId, List<Map<String, Object>> schema) {
        List<Map<String, Object>> answers = jdbcTemplate.query("""
                SELECT answer_data
                FROM survey_answer
                WHERE survey_id = ?
                """, (rs, rowNum) -> parseJsonToMap(rs.getString("answer_data")), surveyId);
        List<Map<String, Object>> stats = new ArrayList<>();
        for (Map<String, Object> question : schema) {
            Map<String, Object> row = new LinkedHashMap<>();
            String questionId = String.valueOf(question.get("id"));
            String type = str(question.getOrDefault("type", "text")).toLowerCase(Locale.ROOT);

            row.put("id", question.get("id"));
            row.put("title", question.get("title"));
            row.put("type", type);
            row.put("required", question.getOrDefault("required", false));

            if ("single".equals(type) || "multi".equals(type)) {
                List<Map<String, Object>> options = listOfMap(question.get("options"));
                Map<String, Integer> countMap = new LinkedHashMap<>();
                for (Map<String, Object> option : options) {
                    countMap.put(str(option.get("label")), 0);
                }
                for (Map<String, Object> answer : answers) {
                    Object value = answer.get(questionId);
                    if (value == null) {
                        continue;
                    }
                    if ("single".equals(type)) {
                        String selected = String.valueOf(value);
                        countMap.computeIfPresent(selected, (k, v) -> v + 1);
                    } else {
                        if (value instanceof List<?> list) {
                            Set<String> uniq = new LinkedHashSet<>(list.stream().map(String::valueOf).toList());
                            for (String item : uniq) {
                                countMap.computeIfPresent(item, (k, v) -> v + 1);
                            }
                        }
                    }
                }
                List<Map<String, Object>> optionStats = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
                    optionStats.add(Map.of("label", entry.getKey(), "count", entry.getValue()));
                }
                row.put("optionStats", optionStats);
            } else if ("rate".equals(type)) {
                int min = intValue(question.get("min"), 1);
                int max = intValue(question.get("max"), 5);
                int total = 0;
                int count = 0;
                Map<Integer, Integer> distribution = new LinkedHashMap<>();
                for (int i = min; i <= max; i++) {
                    distribution.put(i, 0);
                }
                for (Map<String, Object> answer : answers) {
                    Object value = answer.get(questionId);
                    if (value == null) {
                        continue;
                    }
                    int score;
                    try {
                        score = Integer.parseInt(String.valueOf(value));
                    } catch (Exception e) {
                        continue;
                    }
                    total += score;
                    count++;
                    distribution.computeIfPresent(score, (k, v) -> v + 1);
                }
                double avgScore = count == 0 ? 0.0 : (double) total / count;
                row.put("avgScore", Math.round(avgScore * 100.0) / 100.0);
                List<Map<String, Object>> rateStats = new ArrayList<>();
                for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                    rateStats.add(Map.of("score", entry.getKey(), "count", entry.getValue()));
                }
                row.put("rateStats", rateStats);
            } else {
                LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (Map<String, Object> answer : answers) {
                    Object value = answer.get(questionId);
                    if (value != null) {
                        String text = String.valueOf(value).trim();
                        if (!text.isBlank()) {
                            unique.add(text);
                        }
                    }
                }
                List<String> allAnswers = new ArrayList<>(unique);
                List<String> samples = allAnswers.stream().limit(TEXT_SAMPLE_LIMIT).toList();
                row.put("textSamples", samples);
                row.put("textAnswers", allAnswers);
                row.put("textCount", allAnswers.size());
                String summary = allAnswers.isEmpty()
                        ? "暂无文本回答"
                        : String.join("；", samples);
                row.put("textSummary", summary);
            }
            stats.add(row);
        }
        return stats;
    }

    private SurveyDetailResponse toDetail(SurveyModel model) {
        return new SurveyDetailResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                model.getStatus(),
                cloneSchema(model.getSchema()),
                model.getCreatorId(),
                model.isAllowDuplicateSubmit()
        );
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static String formatSurveyTarget(Long id, String title) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isBlank()) {
            return "问卷ID-" + id;
        }
        return "问卷ID-" + safeTitle;
    }

    private static String resolveTerminalType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("openharmony") || ua.contains("harmony") || ua.contains("arkweb") || ua.contains("hmos")) {
            return "鸿蒙";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "平板";
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "移动端";
        }
        return "PC";
    }

    private static String resolveSourceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "直接链接";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("micromessenger")) {
            return "微信";
        }
        return "直接链接";
    }

    private static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String text = ip.trim();
        if (text.isBlank()) {
            return null;
        }
        if (text.length() > 45) {
            return text.substring(0, 45);
        }
        return text;
    }

    private static String normalizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String text = userAgent.trim();
        if (text.isBlank()) {
            return null;
        }
        if (text.length() > 255) {
            return text.substring(0, 255);
        }
        return text;
    }

    private static boolean hasQuota(SurveyModel model) {
        return model.isQuotaEnabled() && model.getQuotaTotal() != null && model.getQuotaTotal() > 0;
    }

    private static boolean boolFlag(Boolean value) {
        return value != null && value;
    }

    private boolean hasSubmitted(Long surveyId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM survey_answer
                WHERE survey_id = ? AND user_id = ?
                """, Integer.class, surveyId, userId);
        return count != null && count > 0;
    }

    private long countAnswers(Long surveyId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM survey_answer
                WHERE survey_id = ?
                """, Long.class, surveyId);
        return count == null ? 0L : count;
    }

    private List<Map<String, Object>> parseSchema(String schemaJson) {
        try {
            if (schemaJson == null || schemaJson.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(schemaJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseJsonToMap(Object rawJson) {
        if (rawJson == null) {
            return new HashMap<>();
        }
        try {
            String text = String.valueOf(rawJson);
            if (text.isBlank()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "JSON 序列化失败");
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMap(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static List<Map<String, Object>> cloneSchema(List<Map<String, Object>> schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : schema) {
            result.add(new LinkedHashMap<>(item));
        }
        return result;
    }
}







