package com.surver.sys.houduan.module.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        long surveyTotal,
        long publishedSurveyTotal,
        long userTotal,
        long enabledUserTotal,
        List<RecentSurveyItem> recentSurveys,
        TrendSeries trend,
        List<DistributionItem> terminalStats,
        List<DistributionItem> sourceStats,
        List<RankingItem> hotSurveyRanking,
        List<TimelineItem> operationTimeline
) {
    public record RecentSurveyItem(
            Long id,
            String title,
            String status,
            String createdAt
    ) {
    }

    public record TrendSeries(
            TrendItem today,
            TrendItem week,
            TrendItem month
    ) {
    }

    public record TrendItem(
            List<String> axis,
            List<Long> values
    ) {
    }

    public record DistributionItem(
            String name,
            long value
    ) {
    }

    public record RankingItem(
            Long id,
            String title,
            long count,
            int percent,
            int rank
    ) {
    }

    public record TimelineItem(
            String id,
            String time,
            String text,
            String type
    ) {
    }
}
