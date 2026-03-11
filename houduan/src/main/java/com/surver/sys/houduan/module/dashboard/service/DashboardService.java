package com.surver.sys.houduan.module.dashboard.service;

import com.surver.sys.houduan.module.dashboard.dto.DashboardResponse;
import com.surver.sys.houduan.module.log.dto.LogItemResponse;
import com.surver.sys.houduan.module.log.service.LogServiceApi;
import com.surver.sys.houduan.module.survey.service.SurveyTitleCodec;
import com.surver.sys.houduan.security.UserPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Profile("!nodeps")
public class DashboardService implements DashboardServiceApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_AXIS_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final LogServiceApi logService;

    public DashboardService(JdbcTemplate jdbcTemplate, LogServiceApi logService) {
        this.jdbcTemplate = jdbcTemplate;
        this.logService = logService;
    }

    @Override
    public DashboardResponse getDashboard(UserPrincipal principal) {
        List<SurveySummary> surveys = listAccessibleSurveys(principal);
        long surveyTotal = surveys.size();
        long publishedSurveyTotal = surveys.stream()
                .filter(item -> "PUBLISHED".equals(item.status()))
                .count();

        long userTotal = queryCount("SELECT COUNT(1) FROM sys_user");
        long enabledUserTotal = queryCount("SELECT COUNT(1) FROM sys_user WHERE status = 1");

        List<DashboardResponse.RecentSurveyItem> recentSurveys = surveys.stream()
                .limit(6)
                .map(item -> new DashboardResponse.RecentSurveyItem(
                        item.id(),
                        item.title(),
                        item.status(),
                        item.createdAt().format(DATE_TIME_FORMATTER)
                ))
                .toList();

        List<AnswerSnapshot> answers = listAnswerSnapshots(surveys);

        DashboardResponse.TrendSeries trend = buildTrendSeries(answers);
        List<DashboardResponse.DistributionItem> terminalStats = buildTerminalStats(answers);
        List<DashboardResponse.DistributionItem> sourceStats = buildSourceStats(answers);
        List<DashboardResponse.RankingItem> hotSurveyRanking = buildHotRanking(surveys, answers);
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

    private List<SurveySummary> listAccessibleSurveys(UserPrincipal principal) {
        if ("ROLE3".equals(principal.role())) {
            return jdbcTemplate.query("""
                    SELECT id, title, status, created_at
                    FROM survey_info
                    WHERE status <> 'DELETED'
                    ORDER BY created_at DESC, id DESC
                    """, (rs, rowNum) -> new SurveySummary(
                    rs.getLong("id"),
                    SurveyTitleCodec.repairLegacyTitle(rs.getString("title"), rs.getLong("id")),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toLocalDateTime()
            ));
        }

        return jdbcTemplate.query("""
                SELECT DISTINCT s.id, s.title, s.status, s.created_at
                FROM survey_info s
                LEFT JOIN survey_auth a ON a.survey_id = s.id AND a.grantee_user_id = ?
                WHERE s.status <> 'DELETED'
                  AND (s.creator_user_id = ? OR a.grantee_user_id IS NOT NULL)
                ORDER BY s.created_at DESC, s.id DESC
                """, (rs, rowNum) -> new SurveySummary(
                rs.getLong("id"),
                SurveyTitleCodec.repairLegacyTitle(rs.getString("title"), rs.getLong("id")),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), principal.userId(), principal.userId());
    }

    private List<AnswerSnapshot> listAnswerSnapshots(List<SurveySummary> surveys) {
        if (surveys.isEmpty()) {
            return List.of();
        }
        List<Long> surveyIds = surveys.stream().map(SurveySummary::id).toList();
        String placeholders = surveyIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT survey_id, submit_time, user_agent FROM survey_answer WHERE survey_id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql, surveyIds.toArray(), (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("submit_time");
            LocalDateTime submitTime = ts == null ? null : ts.toLocalDateTime();
            return new AnswerSnapshot(
                    rs.getLong("survey_id"),
                    submitTime,
                    rs.getString("user_agent")
            );
        });
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
            String terminal = resolveTerminalType(answer.userAgent());
            if (terminal == null || terminal.isBlank() || "未知".equals(terminal)) {
                terminal = "PC";
            }
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
            String source = resolveSourceType(answer.userAgent());
            counts.put(source, counts.getOrDefault(source, 0L) + 1);
        }

        return counts.entrySet().stream()
                .map(entry -> new DashboardResponse.DistributionItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DashboardResponse.RankingItem> buildHotRanking(List<SurveySummary> surveys,
                                                                List<AnswerSnapshot> answers) {
        Map<Long, Long> counts = new HashMap<>();
        for (AnswerSnapshot answer : answers) {
            counts.merge(answer.surveyId(), 1L, Long::sum);
        }

        List<SurveySummary> sorted = new ArrayList<>(surveys);
        sorted.sort(Comparator.comparingLong((SurveySummary item) -> counts.getOrDefault(item.id(), 0L))
                .reversed()
                .thenComparing(SurveySummary::id, Comparator.reverseOrder()));

        int limit = Math.min(5, sorted.size());
        long maxCount = sorted.stream()
                .limit(limit)
                .mapToLong(item -> counts.getOrDefault(item.id(), 0L))
                .max()
                .orElse(0L);

        List<DashboardResponse.RankingItem> ranking = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            SurveySummary item = sorted.get(i);
            long count = counts.getOrDefault(item.id(), 0L);
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

    private long queryCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private record SurveySummary(Long id, String title, String status, LocalDateTime createdAt) {
    }

    private record AnswerSnapshot(Long surveyId, LocalDateTime submitTime, String userAgent) {
    }
}
