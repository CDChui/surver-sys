package com.surver.sys.houduan.module.dashboard.service;

import com.surver.sys.houduan.module.dashboard.dto.DashboardResponse;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import com.surver.sys.houduan.module.log.service.LogServiceApi;
import com.surver.sys.houduan.module.survey.dto.SurveyListItemResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyResponseItemResponse;
import com.surver.sys.houduan.module.survey.dto.SurveyResponseListResponse;
import com.surver.sys.houduan.module.survey.service.SurveyServiceApi;
import com.surver.sys.houduan.module.user.dto.UserItemResponse;
import com.surver.sys.houduan.module.user.service.UserServiceApi;
import com.surver.sys.houduan.security.UserPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("nodeps")
public class LocalDashboardService implements DashboardServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_AXIS_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final SurveyServiceApi surveyService;
    private final UserServiceApi userService;
    private final LogServiceApi logService;

    public LocalDashboardService(SurveyServiceApi surveyService,
                                 UserServiceApi userService,
                                 LogServiceApi logService) {
        this.surveyService = surveyService;
        this.userService = userService;
        this.logService = logService;
    }

    @Override
    public DashboardResponse getDashboard(UserPrincipal principal) {
        List<SurveyListItemResponse> surveys = surveyService.listSurveys(principal);
        List<SurveySummary> summaries = surveys.stream()
                .map(item -> new SurveySummary(
                        item.id(),
                        item.title(),
                        item.status(),
                        parseDateTime(item.createdAt())
                ))
                .toList();

        long surveyTotal = summaries.size();
        long publishedSurveyTotal = summaries.stream()
                .filter(item -> "PUBLISHED".equals(item.status()))
                .count();

        List<UserItemResponse> users = userService.listUsers();
        long userTotal = users.size();
        long enabledUserTotal = users.stream()
                .filter(item -> "ENABLED".equals(item.status()))
                .count();

        List<SurveySummary> sorted = new ArrayList<>(summaries);
        sorted.sort(Comparator.comparing(SurveySummary::createdAt).reversed()
                .thenComparing(SurveySummary::id, Comparator.reverseOrder()));

        List<DashboardResponse.RecentSurveyItem> recentSurveys = sorted.stream()
                .limit(6)
                .map(item -> new DashboardResponse.RecentSurveyItem(
                        item.id(),
                        item.title(),
                        item.status(),
                        formatDateTime(item.createdAt())
                ))
                .toList();

        AnswerAggregation aggregation = collectAnswers(principal, summaries);

        DashboardResponse.TrendSeries trend = buildTrendSeries(aggregation.answers());
        List<DashboardResponse.DistributionItem> terminalStats = buildTerminalStats(aggregation.answers());
        List<DashboardResponse.DistributionItem> sourceStats = buildSourceStats(aggregation.answers());
        List<DashboardResponse.RankingItem> hotSurveyRanking = buildHotRanking(sorted, aggregation.countMap());
        List<DashboardResponse.TimelineItem> operationTimeline = buildTimeline();

        return new DashboardResponse(
                surveyTotal,
                publishedSurveyTotal,
                userTotal,
                enabledUserTotal,
                recentSurveys,
                trend,
                terminalStats,
                sourceStats,
                hotSurveyRanking,
                operationTimeline
        );
    }

    private AnswerAggregation collectAnswers(UserPrincipal principal, List<SurveySummary> summaries) {
        List<AnswerSnapshot> answers = new ArrayList<>();
        Map<Long, Long> countMap = new HashMap<>();

        for (SurveySummary summary : summaries) {
            SurveyResponseListResponse response = surveyService.listSurveyResponses(principal, summary.id());
            List<SurveyResponseItemResponse> rows = response.responses();
            if (rows == null) {
                continue;
            }
            for (SurveyResponseItemResponse row : rows) {
                LocalDateTime submitTime = parseDateTime(row.submitTime());
                answers.add(new AnswerSnapshot(
                        summary.id(),
                        submitTime,
                        row.terminalType(),
                        row.sourceType()
                ));
                countMap.merge(summary.id(), 1L, Long::sum);
            }
        }

        return new AnswerAggregation(answers, countMap);
    }

    private DashboardResponse.TrendSeries buildTrendSeries(List<AnswerSnapshot> answers) {
        long[] todayBuckets = new long[12];
        Map<LocalDate, Long> weekCounts = new HashMap<>();
        Map<LocalDate, Long> monthCounts = new HashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate monthStart = today.minusDays(29);

        for (AnswerSnapshot answer : answers) {
            if (answer.submitTime() == null) {
                continue;
            }
            LocalDateTime time = answer.submitTime();
            LocalDate date = time.toLocalDate();

            if (date.equals(today)) {
                int bucket = Math.min(time.getHour() / 2, 11);
                todayBuckets[bucket]++;
            }

            if (!date.isBefore(weekStart) && !date.isAfter(today)) {
                weekCounts.merge(date, 1L, Long::sum);
            }

            if (!date.isBefore(monthStart) && !date.isAfter(today)) {
                monthCounts.merge(date, 1L, Long::sum);
            }
        }

        List<String> todayAxis = new ArrayList<>();
        List<Long> todayValues = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            todayAxis.add(String.format("%02d:00", i * 2));
            todayValues.add(todayBuckets[i]);
        }

        List<String> weekAxis = new ArrayList<>();
        List<Long> weekValues = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            weekAxis.add(date.format(DATE_AXIS_FORMATTER));
            weekValues.add(weekCounts.getOrDefault(date, 0L));
        }

        List<String> monthAxis = new ArrayList<>();
        List<Long> monthValues = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate date = monthStart.plusDays(i);
            monthAxis.add(date.format(DATE_AXIS_FORMATTER));
            monthValues.add(monthCounts.getOrDefault(date, 0L));
        }

        return new DashboardResponse.TrendSeries(
                new DashboardResponse.TrendItem(todayAxis, todayValues),
                new DashboardResponse.TrendItem(weekAxis, weekValues),
                new DashboardResponse.TrendItem(monthAxis, monthValues)
        );
    }

    private List<DashboardResponse.DistributionItem> buildTerminalStats(List<AnswerSnapshot> answers) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("移动端", 0L);
        counts.put("PC", 0L);
        counts.put("平板", 0L);
        counts.put("鸿蒙", 0L);

        for (AnswerSnapshot answer : answers) {
            String terminal = normalizeTerminalType(answer.terminalType());
            if (counts.containsKey(terminal)) {
                counts.put(terminal, counts.get(terminal) + 1);
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new DashboardResponse.DistributionItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardResponse.DistributionItem> buildSourceStats(List<AnswerSnapshot> answers) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("微信", 0L);
        counts.put("直接链接", 0L);

        for (AnswerSnapshot answer : answers) {
            String source = normalizeSourceType(answer.sourceType());
            counts.put(source, counts.getOrDefault(source, 0L) + 1);
        }

        return counts.entrySet().stream()
                .map(entry -> new DashboardResponse.DistributionItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardResponse.RankingItem> buildHotRanking(List<SurveySummary> surveys,
                                                                Map<Long, Long> countMap) {
        List<SurveySummary> sorted = new ArrayList<>(surveys);
        sorted.sort(Comparator.comparingLong((SurveySummary item) -> countMap.getOrDefault(item.id(), 0L))
                .reversed()
                .thenComparing(SurveySummary::id, Comparator.reverseOrder()));

        int limit = Math.min(5, sorted.size());
        long maxCount = sorted.stream()
                .limit(limit)
                .mapToLong(item -> countMap.getOrDefault(item.id(), 0L))
                .max()
                .orElse(0L);

        List<DashboardResponse.RankingItem> ranking = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            SurveySummary item = sorted.get(i);
            long count = countMap.getOrDefault(item.id(), 0L);
            int percent = maxCount == 0 ? 0 : Math.max(8, (int) Math.round(count * 100.0 / maxCount));
            ranking.add(new DashboardResponse.RankingItem(
                    item.id(),
                    item.title(),
                    count,
                    percent,
                    i + 1
            ));
        }
        return ranking;
    }

    private List<DashboardResponse.TimelineItem> buildTimeline() {
        List<LogItemResponse> logs = logService.listLogs("SYSTEM", "DESC");
        List<DashboardResponse.TimelineItem> timeline = new ArrayList<>();

        for (LogItemResponse log : logs.stream().limit(6).toList()) {
            String text = buildLogText(log);
            timeline.add(new DashboardResponse.TimelineItem(
                    "log-" + log.id(),
                    log.createdAt(),
                    text,
                    resolveTimelineType(log.action())
            ));
        }

        if (timeline.isEmpty()) {
            timeline.add(new DashboardResponse.TimelineItem(
                    "system-default",
                    "刚刚",
                    "系统已就绪，等待新的问卷操作",
                    "system"
            ));
        }

        return timeline;
    }

    private String buildLogText(LogItemResponse log) {
        String operator = log.operator();
        if (operator == null || operator.isBlank()) {
            operator = "管理员";
        }
        String actionText = mapActionText(log.action());
        String moduleText = mapModuleText(log.module());
        StringBuilder builder = new StringBuilder();
        builder.append(operator).append(actionText).append("了").append(moduleText);
        String target = log.target();
        if (target != null && !target.isBlank()) {
            builder.append(" ").append(target);
        }
        return builder.toString();
    }

    private static String mapActionText(String action) {
        if ("CREATE".equals(action)) return "创建";
        if ("UPDATE".equals(action)) return "更新";
        if ("DELETE".equals(action)) return "删除";
        if ("PUBLISH".equals(action)) return "发布";
        if ("CLOSE".equals(action)) return "关闭";
        if ("LOGIN".equals(action)) return "登录";
        if ("LOGOUT".equals(action)) return "退出";
        return "操作";
    }

    private static String mapModuleText(String module) {
        if ("SURVEY".equals(module)) return "问卷";
        if ("USER".equals(module)) return "用户";
        if ("PERMISSION".equals(module)) return "权限";
        return "系统";
    }

    private static String resolveTimelineType(String action) {
        if ("CREATE".equals(action)) return "create";
        if ("PUBLISH".equals(action)) return "publish";
        if ("CLOSE".equals(action)) return "close";
        return "system";
    }

    private static String normalizeTerminalType(String terminalType) {
        if (terminalType == null || terminalType.isBlank() || "未知".equals(terminalType)) {
            return "PC";
        }
        if ("移动端".equals(terminalType) || "PC".equals(terminalType) || "平板".equals(terminalType) || "鸿蒙".equals(terminalType)) {
            return terminalType;
        }
        return terminalType;
    }

    private static String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "直接链接";
        }
        if ("微信".equals(sourceType)) {
            return "微信";
        }
        return "直接链接";
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.MIN;
        }
    }

    private static String formatDateTime(LocalDateTime value) {
        if (value == null || value.equals(LocalDateTime.MIN)) {
            return "";
        }
        return value.format(DATE_TIME_FORMATTER);
    }

    private record SurveySummary(Long id, String title, String status, LocalDateTime createdAt) {
    }

    private record AnswerSnapshot(Long surveyId, LocalDateTime submitTime, String terminalType, String sourceType) {
    }

    private record AnswerAggregation(List<AnswerSnapshot> answers, Map<Long, Long> countMap) {
    }
}
