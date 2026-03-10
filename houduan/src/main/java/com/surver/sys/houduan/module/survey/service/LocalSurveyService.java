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
import com.surver.sys.houduan.module.survey.dto.SurveyStatsResponse;
import com.surver.sys.houduan.module.survey.dto.UpdateSurveyRequest;
import com.surver.sys.houduan.module.survey.model.SurveyModel;
import com.surver.sys.houduan.module.user.model.UserModel;
import com.surver.sys.houduan.module.user.service.UserServiceApi;
import com.surver.sys.houduan.security.UserPrincipal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("nodeps")
public class LocalSurveyService implements SurveyServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long ENTRY_TOKEN_TTL_MILLIS = 30 * 60 * 1000L;
    private static final long SUBMIT_LOCK_TTL_MILLIS = 10 * 1000L;
    private static final Path SURVEY_STORE_FILE = Path.of(".nodeps-data", "surveys.json").toAbsolutePath().normalize();
    private static final Path LEGACY_SURVEY_STORE_FILE = Path.of("..", ".nodeps-data", "surveys.json").toAbsolutePath().normalize();

    private final ObjectMapper objectMapper;
    private final UserServiceApi userService;
    private final LogServiceApi logService;

    private final AtomicLong surveyIdGenerator = new AtomicLong(1000);
    private final Map<Long, SurveyModel> surveyStore = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, SurveyAnswerRecord>> answerStore = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, EntryTokenRecord>> entryTokenStore = new ConcurrentHashMap<>();
    private final Map<String, Long> submitLocks = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> quotaCountStore = new ConcurrentHashMap<>();

    public LocalSurveyService(ObjectMapper objectMapper,
                              UserServiceApi userService,
                              LogServiceApi logService) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.logService = logService;
        loadSurveyStore();
    }

    @Override
    public SurveyDetailResponse createSurvey(UserPrincipal principal, CreateSurveyRequest request) {
        String normalizedTitle = SurveyTitleCodec.normalizeInputTitle(request.title());
        if (SurveyTitleCodec.isLikelyBrokenTitle(normalizedTitle)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "问卷标题编码异常，请使用 UTF-8 客户端提交");
        }
        SurveyModel model = new SurveyModel();
        model.setId(surveyIdGenerator.incrementAndGet());
        model.setTitle(normalizedTitle);
        model.setDescription(trim(request.description()));
        model.setStatus("DRAFT");
        model.setCreatorId(principal.userId());
        model.setCreatedAt(nowText());
        model.setAllowDuplicateSubmit(boolValue(request.allowDuplicateSubmit()));
        model.setQuotaEnabled(false);
        model.setQuotaTotal(null);
        model.setSchema(cloneSchema(request.questions()));
        surveyStore.put(model.getId(), model);
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "SURVEY", "CREATE", model.getTitle());
        return toDetail(model);
    }

    @Override
    public List<SurveyListItemResponse> listSurveys(UserPrincipal principal) {
        if ("ROLE1".equals(principal.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "forbidden");
        }
        repairStoredTitlesIfNeeded();
        return surveyStore.values().stream()
                .filter(model -> !"DELETED".equals(model.getStatus()))
                .filter(model -> canAccessSurvey(principal, model.getId(), model.getCreatorId()))
                .sorted(Comparator.comparing(SurveyModel::getId))
                .map(model -> new SurveyListItemResponse(
                        model.getId(),
                        model.getTitle(),
                        model.getStatus(),
                        model.getCreatedAt(),
                        model.getCreatorId()
                ))
                .toList();
    }

    @Override
    public SurveyDetailResponse getSurveyDetail(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        if (!canAccessSurvey(principal, model.getId(), model.getCreatorId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "forbidden");
        }
        return toDetail(model);
    }

    @Override
    public SurveyDetailResponse updateSurvey(UserPrincipal principal, Long id, UpdateSurveyRequest request) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        String normalizedTitle = SurveyTitleCodec.normalizeInputTitle(request.title());
        if (SurveyTitleCodec.isLikelyBrokenTitle(normalizedTitle)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "问卷标题编码异常，请使用 UTF-8 客户端提交");
        }
        model.setTitle(normalizedTitle);
        model.setDescription(trim(request.description()));
        model.setAllowDuplicateSubmit(boolValue(request.allowDuplicateSubmit()));
        model.setSchema(cloneSchema(request.questions()));
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE", model.getTitle());
        return toDetail(model);
    }

    @Override
    public void publishSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        model.setStatus("PUBLISHED");
        if (hasQuota(model)) {
            quotaCountStore.put(id, new AtomicLong(countAnswers(id)));
        }
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "SURVEY", "PUBLISH", model.getTitle());
    }

    @Override
    public void closeSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        model.setStatus("CLOSED");
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "SURVEY", "CLOSE", model.getTitle());
    }

    @Override
    public void deleteSurvey(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        model.setStatus("DELETED");
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "SURVEY", "DELETE", model.getTitle());
    }

    @Override
    public PublicSurveyResponse getPublicSurvey(UserPrincipal principal, Long id, boolean previewMode) {
        SurveyModel model = getSurvey(id);
        ensureSurveyReadableByPrincipal(principal, model);

        boolean allowDuplicateSubmit = model.isAllowDuplicateSubmit();
        boolean submitted = hasSubmitted(id, principal.userId());
        if (!previewMode && !allowDuplicateSubmit && submitted) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "duplicate submit");
        }

        String entryToken = null;
        if (!previewMode && hasQuota(model)) {
            long quotaCount = getOrInitQuotaCount(id);
            boolean hasTicket = hasEntryToken(id, principal.userId());
            if (quotaCount >= model.getQuotaTotal() && !hasTicket && !submitted) {
                throw new BizException(ErrorCode.QUOTA_FULL, "quota full");
            }
            entryToken = issueEntryToken(id, principal.userId());
        }

        return new PublicSurveyResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                cloneSchema(model.getSchema()),
                entryToken
        );
    }

    @Override
    public SubmitSurveyResponse submitSurvey(UserPrincipal principal, Long id, SubmitSurveyRequest request) {
        if (!Objects.equals(id, request.surveyId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "surveyId mismatch");
        }
        SurveyModel model = getSurvey(id);
        ensureSurveyReadableByPrincipal(principal, model);
        boolean previewMode = boolValue(request.previewMode());

        boolean allowDuplicateSubmit = model.isAllowDuplicateSubmit();
        boolean submittedBefore = hasSubmitted(id, principal.userId());
        if (!previewMode && !allowDuplicateSubmit && submittedBefore) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "duplicate submit");
        }

        if (!tryAcquireSubmitLock(id, principal.userId())) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "submit too frequent");
        }

        try {
            if (!previewMode && hasQuota(model) && !submittedBefore) {
                String storedToken = getEntryToken(id, principal.userId());
                if (storedToken == null || storedToken.isBlank()) {
                    throw new BizException(ErrorCode.QUOTA_FULL, "entry token missing");
                }
                String requestToken = request.entryToken();
                if (requestToken != null && !requestToken.isBlank() && !Objects.equals(requestToken, storedToken)) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "invalid entry token");
                }
                long quotaCount = getOrInitQuotaCount(id);
                if (quotaCount >= model.getQuotaTotal()) {
                    throw new BizException(ErrorCode.QUOTA_FULL, "quota full");
                }
            }

            if (previewMode) {
                String submitTime = nowText();
                logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE", "preview submit surveyId=" + id);
                return new SubmitSurveyResponse(id, submitTime);
            }

            Map<Long, SurveyAnswerRecord> surveyAnswers = answerStore.computeIfAbsent(id, key -> new ConcurrentHashMap<>());
            boolean inserted = false;
            String submitTime = nowText();
            Map<String, Object> answerCopy = deepCopyMap(request.answers());
            String titleSnapshot = model.getTitle();
            String descriptionSnapshot = model.getDescription();
            List<Map<String, Object>> schemaSnapshot = cloneSchema(model.getSchema());

            SurveyAnswerRecord existed = surveyAnswers.get(principal.userId());
            if (allowDuplicateSubmit) {
                if (existed == null) {
                    UserModel currentUser = userService.getById(principal.userId());
                    SurveyAnswerRecord created = new SurveyAnswerRecord(
                            principal.userId(),
                            currentUser.getUsername(),
                            currentUser.getRealName(),
                            titleSnapshot,
                            descriptionSnapshot,
                            schemaSnapshot,
                            answerCopy,
                            submitTime
                    );
                    surveyAnswers.put(principal.userId(), created);
                    inserted = true;
                } else {
                    surveyAnswers.put(principal.userId(), existed.withAnswers(
                            titleSnapshot,
                            descriptionSnapshot,
                            schemaSnapshot,
                            answerCopy,
                            submitTime
                    ));
                }
            } else {
                UserModel currentUser = userService.getById(principal.userId());
                SurveyAnswerRecord created = new SurveyAnswerRecord(
                        principal.userId(),
                        currentUser.getUsername(),
                        currentUser.getRealName(),
                        titleSnapshot,
                        descriptionSnapshot,
                        schemaSnapshot,
                        answerCopy,
                        submitTime
                );
                surveyAnswers.put(principal.userId(), created);
                inserted = true;
            }

            if (hasQuota(model) && inserted) {
                incrementQuotaCount(id);
                clearEntryToken(id, principal.userId());
            }

            saveSurveyStore();
            logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE", "submit surveyId=" + id);
            return new SubmitSurveyResponse(id, submitTime);
        } finally {
            releaseSubmitLock(id, principal.userId());
        }
    }

    @Override
    public List<MySurveySubmissionItemResponse> listMySubmissions(UserPrincipal principal) {
        List<MySurveySubmissionItemResponse> result = new ArrayList<>();

        for (Map.Entry<Long, Map<Long, SurveyAnswerRecord>> entry : answerStore.entrySet()) {
            Long surveyId = entry.getKey();
            SurveyAnswerRecord record = entry.getValue().get(principal.userId());
            if (record == null) {
                continue;
            }

            result.add(new MySurveySubmissionItemResponse(
                    surveyId,
                    SurveyTitleCodec.repairLegacyTitle(record.surveyTitleSnapshot(), surveyId),
                    record.submitTime()
            ));
        }

        result.sort(Comparator.comparing(MySurveySubmissionItemResponse::submitTime).reversed());
        return result;
    }

    @Override
    public MySurveySubmissionDetailResponse getMySubmissionDetail(UserPrincipal principal, Long id) {
        Map<Long, SurveyAnswerRecord> surveyAnswers = answerStore.getOrDefault(id, Collections.emptyMap());
        SurveyAnswerRecord record = surveyAnswers.get(principal.userId());
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到已提交记录");
        }

        return new MySurveySubmissionDetailResponse(
                id,
                SurveyTitleCodec.repairLegacyTitle(record.surveyTitleSnapshot(), id),
                record.surveyDescriptionSnapshot(),
                cloneSchema(record.surveySchemaSnapshot()),
                deepCopyMap(record.answers()),
                record.submitTime()
        );
    }

    @Override
    public SurveyStatsResponse getSurveyStats(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        List<Map<String, Object>> statsList = buildStats(id, model.getSchema());
        return new SurveyStatsResponse(
                model.getId(),
                model.getTitle(),
                model.getDescription(),
                cloneSchema(model.getSchema()),
                statsList
        );
    }

    @Override
    public byte[] exportSurveyData(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("答卷明细");
            Row header = sheet.createRow(0);
            int headerCol = 0;
            header.createCell(headerCol++).setCellValue("提交时间");
            header.createCell(headerCol++).setCellValue("账号");
            header.createCell(headerCol++).setCellValue("用户名");
            for (Map<String, Object> question : model.getSchema()) {
                header.createCell(headerCol++).setCellValue(str(question.get("title")));
            }

            List<SurveyAnswerRecord> rows = listSurveyAnswers(id);
            int rowIndex = 1;
            for (SurveyAnswerRecord rowData : rows) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(rowData.submitTime());
                row.createCell(col++).setCellValue(rowData.username());
                row.createCell(col++).setCellValue(rowData.realName());
                for (Map<String, Object> question : model.getSchema()) {
                    String qid = str(question.get("id"));
                    Object value = rowData.answers().get(qid);
                    if (value == null) {
                        row.createCell(col++).setCellValue("");
                    } else if (value instanceof List<?> list) {
                        row.createCell(col++).setCellValue(String.join("、", list.stream().map(String::valueOf).toList()));
                    } else {
                        row.createCell(col++).setCellValue(String.valueOf(value));
                    }
                }
            }

            workbook.write(out);
            logService.appendSystemLog(principal.username(), "SURVEY", "UPDATE", "export surveyId=" + id);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "export failed: " + e.getMessage());
        }
    }

    @Override
    public List<SurveyAuthUserDto> listAuthUsers(UserPrincipal principal, Long id) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        return model.getAuthUsers().stream()
                .sorted(Comparator.comparing(SurveyAuthUserDto::userId))
                .map(item -> new SurveyAuthUserDto(item.userId(), item.username(), item.realName()))
                .toList();
    }

    @Override
    public void addAuthUser(UserPrincipal principal, Long id, SurveyAuthUserDto user) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());

        boolean existed = model.getAuthUsers().stream().anyMatch(item -> Objects.equals(item.userId(), user.userId()));
        if (existed) {
            return;
        }

        UserModel target = userService.getById(user.userId());
        SurveyAuthUserDto item = new SurveyAuthUserDto(
                target.getId(),
                target.getUsername(),
                target.getRealName()
        );
        model.getAuthUsers().add(item);
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "PERMISSION", "UPDATE",
                "grant surveyId=" + id + ", userId=" + target.getId());
    }

    @Override
    public void removeAuthUser(UserPrincipal principal, Long id, Long userId) {
        SurveyModel model = getSurvey(id);
        ensureCanManageSurvey(principal, model.getId(), model.getCreatorId());
        model.getAuthUsers().removeIf(item -> Objects.equals(item.userId(), userId));
        saveSurveyStore();
        logService.appendSystemLog(principal.username(), "PERMISSION", "UPDATE",
                "revoke surveyId=" + id + ", userId=" + userId);
    }

    private SurveyModel getSurvey(Long id) {
        SurveyModel model = surveyStore.get(id);
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "survey not found");
        }
        String repairedTitle = SurveyTitleCodec.repairLegacyTitle(model.getTitle(), model.getId());
        if (!Objects.equals(repairedTitle, model.getTitle())) {
            model.setTitle(repairedTitle);
            saveSurveyStore();
        }
        return model;
    }

    private void ensureCanManageSurvey(UserPrincipal principal, Long surveyId, Long creatorId) {
        if (!canAccessSurvey(principal, surveyId, creatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "forbidden");
        }
    }

    private void ensureSurveyReadableByPrincipal(UserPrincipal principal, SurveyModel model) {
        if ("DELETED".equals(model.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "survey not found");
        }
        if (!"PUBLISHED".equals(model.getStatus()) && !isManagerRole(principal)) {
            throw new BizException(ErrorCode.NOT_FOUND, "survey not found");
        }
    }

    private static boolean isManagerRole(UserPrincipal principal) {
        return "ROLE2".equals(principal.role()) || "ROLE3".equals(principal.role());
    }

    private void repairStoredTitlesIfNeeded() {
        boolean changed = false;
        for (SurveyModel model : surveyStore.values()) {
            String repairedTitle = SurveyTitleCodec.repairLegacyTitle(model.getTitle(), model.getId());
            if (!Objects.equals(repairedTitle, model.getTitle())) {
                model.setTitle(repairedTitle);
                changed = true;
            }
        }
        if (changed) {
            saveSurveyStore();
        }
    }

    private boolean canAccessSurvey(UserPrincipal principal, Long surveyId, Long creatorId) {
        if ("ROLE3".equals(principal.role())) {
            return true;
        }
        if (!"ROLE2".equals(principal.role())) {
            return false;
        }
        if (Objects.equals(creatorId, principal.userId())) {
            return true;
        }
        SurveyModel model = surveyStore.get(surveyId);
        if (model == null) {
            return false;
        }
        return model.getAuthUsers().stream().anyMatch(item -> Objects.equals(item.userId(), principal.userId()));
    }

    private void loadSurveyStore() {
        Path readPath = resolveSurveyStoreReadPath();
        if (!Files.exists(readPath)) {
            return;
        }

        try {
            Map<String, Object> snapshot = objectMapper.readValue(
                    readPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            if (snapshot == null) {
                return;
            }

            surveyStore.clear();
            answerStore.clear();
            long maxId = 1000L;
            List<Map<String, Object>> surveys = listOfMap(snapshot.get("surveys"));
            for (Map<String, Object> item : surveys) {
                Long surveyId = longValue(item.get("id"), null);
                if (surveyId == null) {
                    continue;
                }

                SurveyModel model = new SurveyModel();
                model.setId(surveyId);
                model.setTitle(str(item.get("title")));
                model.setDescription(str(item.get("description")));
                model.setStatus(str(item.getOrDefault("status", "DRAFT")));
                model.setCreatorId(longValue(item.get("creatorId"), 0L));
                model.setCreatedAt(str(item.getOrDefault("createdAt", nowText())));
                model.setAllowDuplicateSubmit(boolValue(item.get("allowDuplicateSubmit")));
                model.setQuotaEnabled(boolValue(item.get("quotaEnabled")));
                model.setQuotaTotal(nullableIntValue(item.get("quotaTotal")));
                model.setSchema(cloneSchema(listOfMap(item.get("schema"))));
                model.getAuthUsers().clear();

                List<Map<String, Object>> authUserList = listOfMap(item.get("authUsers"));
                for (Map<String, Object> authUser : authUserList) {
                    Long userId = longValue(authUser.get("userId"), null);
                    if (userId == null) {
                        continue;
                    }
                    model.getAuthUsers().add(new SurveyAuthUserDto(
                            userId,
                            str(authUser.get("username")),
                            str(authUser.get("realName"))
                    ));
                }
                surveyStore.put(model.getId(), model);
                if (model.getId() > maxId) {
                    maxId = model.getId();
                }
            }

            List<Map<String, Object>> answers = listOfMap(snapshot.get("answers"));
            for (Map<String, Object> item : answers) {
                Long surveyId = longValue(item.get("surveyId"), null);
                Long userId = longValue(item.get("userId"), null);
                if (surveyId == null || userId == null) {
                    continue;
                }

                Map<String, Object> answerData = new LinkedHashMap<>();
                Object rawAnswerData = item.get("answers");
                if (rawAnswerData instanceof Map<?, ?> mapData) {
                    answerData = deepCopyMap((Map<String, Object>) mapData);
                }

                List<Map<String, Object>> schemaSnapshot = cloneSchema(
                        listOfMap(item.get("surveySchemaSnapshot"))
                );

                SurveyAnswerRecord record = new SurveyAnswerRecord(
                        userId,
                        str(item.get("username")),
                        str(item.get("realName")),
                        str(item.get("surveyTitleSnapshot")),
                        str(item.get("surveyDescriptionSnapshot")),
                        schemaSnapshot,
                        answerData,
                        str(item.getOrDefault("submitTime", nowText()))
                );

                answerStore
                        .computeIfAbsent(surveyId, key -> new ConcurrentHashMap<>())
                        .put(userId, record);
            }

            long nextId = Math.max(longValue(snapshot.get("nextSurveyId"), 1000L), maxId);
            surveyIdGenerator.set(nextId);
            if (!Objects.equals(readPath, SURVEY_STORE_FILE)) {
                saveSurveyStore();
            }
        } catch (Exception ignored) {
            // nodeps 下本地持久化失败不阻断服务启动
        }
    }

    private static Path resolveSurveyStoreReadPath() {
        if (Files.exists(SURVEY_STORE_FILE)) {
            return SURVEY_STORE_FILE;
        }
        if (Files.exists(LEGACY_SURVEY_STORE_FILE)) {
            return LEGACY_SURVEY_STORE_FILE;
        }
        return SURVEY_STORE_FILE;
    }

    private synchronized void saveSurveyStore() {
        try {
            Files.createDirectories(SURVEY_STORE_FILE.getParent());

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("nextSurveyId", surveyIdGenerator.get());
            List<Map<String, Object>> surveys = surveyStore.values().stream()
                    .map(model -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", model.getId());
                        item.put("title", model.getTitle());
                        item.put("description", model.getDescription());
                        item.put("status", model.getStatus());
                        item.put("creatorId", model.getCreatorId());
                        item.put("createdAt", model.getCreatedAt());
                        item.put("allowDuplicateSubmit", model.isAllowDuplicateSubmit());
                        item.put("quotaEnabled", model.isQuotaEnabled());
                        item.put("quotaTotal", model.getQuotaTotal());
                        item.put("schema", cloneSchema(model.getSchema()));
                        item.put("authUsers", model.getAuthUsers().stream()
                                .map(authUser -> Map.of(
                                        "userId", authUser.userId(),
                                        "username", authUser.username(),
                                        "realName", authUser.realName()
                                ))
                                .toList());
                        return item;
                    })
                    .sorted(Comparator.comparing(item -> longValue(item.get("id"), 0L)))
                    .toList();
            snapshot.put("surveys", surveys);

            List<Map<String, Object>> answers = answerStore.entrySet().stream()
                    .flatMap(entry -> entry.getValue().values().stream()
                            .map(record -> {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("surveyId", entry.getKey());
                                item.put("userId", record.userId());
                                item.put("username", record.username());
                                item.put("realName", record.realName());
                                item.put("surveyTitleSnapshot", record.surveyTitleSnapshot());
                                item.put("surveyDescriptionSnapshot", record.surveyDescriptionSnapshot());
                                item.put("surveySchemaSnapshot", cloneSchema(record.surveySchemaSnapshot()));
                                item.put("answers", deepCopyMap(record.answers()));
                                item.put("submitTime", record.submitTime());
                                return item;
                            }))
                    .sorted((a, b) -> {
                        Long surveyA = longValue(a.get("surveyId"), 0L);
                        Long surveyB = longValue(b.get("surveyId"), 0L);
                        int surveyCmp = Long.compare(surveyA, surveyB);
                        if (surveyCmp != 0) {
                            return surveyCmp;
                        }
                        return Long.compare(
                                longValue(a.get("userId"), 0L),
                                longValue(b.get("userId"), 0L)
                        );
                    })
                    .toList();
            snapshot.put("answers", answers);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(SURVEY_STORE_FILE.toFile(), snapshot);
        } catch (Exception ignored) {
            // nodeps 下本地持久化失败不阻断主流程
        }
    }

    private static SurveyDetailResponse toDetail(SurveyModel model) {
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

    private boolean hasSubmitted(Long surveyId, Long userId) {
        Map<Long, SurveyAnswerRecord> surveyAnswers = answerStore.get(surveyId);
        return surveyAnswers != null && surveyAnswers.containsKey(userId);
    }

    private long countAnswers(Long surveyId) {
        Map<Long, SurveyAnswerRecord> surveyAnswers = answerStore.get(surveyId);
        return surveyAnswers == null ? 0L : surveyAnswers.size();
    }

    private List<SurveyAnswerRecord> listSurveyAnswers(Long surveyId) {
        Map<Long, SurveyAnswerRecord> surveyAnswers = answerStore.getOrDefault(surveyId, Collections.emptyMap());
        return surveyAnswers.values().stream()
                .sorted(Comparator.comparing(SurveyAnswerRecord::submitTime))
                .toList();
    }

    private long getOrInitQuotaCount(Long surveyId) {
        return quotaCountStore.computeIfAbsent(surveyId, key -> new AtomicLong(countAnswers(key))).get();
    }

    private void incrementQuotaCount(Long surveyId) {
        quotaCountStore.computeIfAbsent(surveyId, key -> new AtomicLong(countAnswers(key))).incrementAndGet();
    }

    private static boolean hasQuota(SurveyModel model) {
        return model.isQuotaEnabled() && model.getQuotaTotal() != null && model.getQuotaTotal() > 0;
    }

    private String issueEntryToken(Long surveyId, Long userId) {
        String token = UUID.randomUUID().toString();
        long expireAt = System.currentTimeMillis() + ENTRY_TOKEN_TTL_MILLIS;
        entryTokenStore.computeIfAbsent(surveyId, key -> new ConcurrentHashMap<>())
                .put(userId, new EntryTokenRecord(token, expireAt));
        return token;
    }

    private boolean hasEntryToken(Long surveyId, Long userId) {
        return getEntryToken(surveyId, userId) != null;
    }

    private String getEntryToken(Long surveyId, Long userId) {
        Map<Long, EntryTokenRecord> tokenMap = entryTokenStore.get(surveyId);
        if (tokenMap == null) {
            return null;
        }
        EntryTokenRecord token = tokenMap.get(userId);
        if (token == null) {
            return null;
        }
        if (token.expireAtMillis() <= System.currentTimeMillis()) {
            tokenMap.remove(userId);
            return null;
        }
        return token.token();
    }

    private void clearEntryToken(Long surveyId, Long userId) {
        Map<Long, EntryTokenRecord> tokenMap = entryTokenStore.get(surveyId);
        if (tokenMap != null) {
            tokenMap.remove(userId);
        }
    }

    private boolean tryAcquireSubmitLock(Long surveyId, Long userId) {
        String key = surveyId + ":" + userId;
        long now = System.currentTimeMillis();
        long expireAt = now + SUBMIT_LOCK_TTL_MILLIS;
        synchronized (submitLocks) {
            Long current = submitLocks.get(key);
            if (current != null && current > now) {
                return false;
            }
            submitLocks.put(key, expireAt);
            return true;
        }
    }

    private void releaseSubmitLock(Long surveyId, Long userId) {
        submitLocks.remove(surveyId + ":" + userId);
    }

    private List<Map<String, Object>> buildStats(Long surveyId, List<Map<String, Object>> schema) {
        List<Map<String, Object>> answers = listSurveyAnswers(surveyId).stream()
                .map(record -> record.answers())
                .toList();
        List<Map<String, Object>> stats = new ArrayList<>();

        for (Map<String, Object> question : schema) {
            Map<String, Object> row = new LinkedHashMap<>();
            String questionId = str(question.get("id"));
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
                    } else if (value instanceof List<?> list) {
                        Set<String> uniq = new LinkedHashSet<>(list.stream().map(String::valueOf).toList());
                        for (String item : uniq) {
                            countMap.computeIfPresent(item, (k, v) -> v + 1);
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
                List<String> samples = new ArrayList<>();
                for (Map<String, Object> answer : answers) {
                    Object value = answer.get(questionId);
                    if (value == null) {
                        continue;
                    }
                    String text = String.valueOf(value).trim();
                    if (!text.isBlank()) {
                        samples.add(text);
                    }
                }
                String summary = samples.isEmpty() ? "" : String.join("; ", samples.stream().limit(3).toList());
                row.put("textSummary", summary);
            }

            stats.add(row);
        }

        return stats;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cloneSchema(List<Map<String, Object>> schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (schema == null) {
            return result;
        }
        for (Map<String, Object> item : schema) {
            result.add((Map<String, Object>) deepCopyValue(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return (Map<String, Object>) deepCopyValue(source);
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Integer nullableIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long longValue(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(str(value));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMap(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) deepCopyValue(map));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private record SurveyAnswerRecord(
            Long userId,
            String username,
            String realName,
            String surveyTitleSnapshot,
            String surveyDescriptionSnapshot,
            List<Map<String, Object>> surveySchemaSnapshot,
            Map<String, Object> answers,
            String submitTime
    ) {
        private SurveyAnswerRecord withAnswers(String titleSnapshot,
                                               String descriptionSnapshot,
                                               List<Map<String, Object>> schemaSnapshot,
                                               Map<String, Object> newAnswers,
                                               String newSubmitTime) {
            return new SurveyAnswerRecord(
                    userId,
                    username,
                    realName,
                    titleSnapshot,
                    descriptionSnapshot,
                    schemaSnapshot,
                    newAnswers,
                    newSubmitTime
            );
        }
    }

    private record EntryTokenRecord(String token, long expireAtMillis) {
    }
}
