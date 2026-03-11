package com.surver.sys.houduan.module.survey.service;

import java.nio.charset.StandardCharsets;

public final class SurveyTitleCodec {

    private SurveyTitleCodec() {
    }

    public static String normalizeInputTitle(String rawTitle) {
        String normalized = rawTitle == null ? "" : rawTitle.trim();
        if (normalized.isBlank()) {
            return normalized;
        }

        String repaired = tryRepairUtf8Mojibake(normalized);
        return repaired.trim();
    }

    public static String repairLegacyTitle(String rawTitle, Long surveyId) {
        String normalized = rawTitle == null ? "" : rawTitle.trim();
        if (normalized.isBlank()) {
            return defaultTitle(surveyId);
        }

        String repaired = tryRepairUtf8Mojibake(normalized).trim();
        if (repaired.isBlank()) {
            return defaultTitle(surveyId);
        }
        if (isLikelyBrokenTitle(repaired)) {
            return defaultTitle(surveyId);
        }
        return repaired;
    }

    public static boolean isLikelyBrokenTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }

        int qCount = 0;
        for (char ch : title.toCharArray()) {
            if (ch == '?' || ch == '？' || ch == '\uFFFD') {
                qCount++;
            }
        }

        return qCount >= 3 && qCount * 2 >= title.length();
    }

    private static String defaultTitle(Long surveyId) {
        if (surveyId == null) {
            return "问卷";
        }
        return "问卷" + surveyId;
    }

    private static String tryRepairUtf8Mojibake(String value) {
        String candidate = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        if (readabilityScore(candidate) > readabilityScore(value) + 2) {
            return candidate;
        }
        return value;
    }

    private static int readabilityScore(String value) {
        int score = 0;
        for (char ch : value.toCharArray()) {
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                score += 3;
                continue;
            }
            if (ch == '?' || ch == '？' || ch == '\uFFFD') {
                score -= 4;
                continue;
            }
            if (ch >= '\u00C0' && ch <= '\u00FF') {
                score -= 1;
            }
        }
        return score;
    }
}
