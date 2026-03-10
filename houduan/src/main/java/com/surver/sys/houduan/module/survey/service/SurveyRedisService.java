package com.surver.sys.houduan.module.survey.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;

@Service
@Profile("!nodeps")
public class SurveyRedisService {

    private static final String ENTRY_PREFIX = "survey:entry:";
    private static final String LOCK_PREFIX = "survey:submit:lock:";
    private static final String QUOTA_PREFIX = "survey:quota:count:";

    private final StringRedisTemplate stringRedisTemplate;

    public SurveyRedisService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String issueEntryToken(Long surveyId, Long userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(buildEntryKey(surveyId, userId), token, ttl);
        return token;
    }

    public boolean hasEntryToken(Long surveyId, Long userId) {
        String value = stringRedisTemplate.opsForValue().get(buildEntryKey(surveyId, userId));
        return value != null && !value.isBlank();
    }

    public String getEntryToken(Long surveyId, Long userId) {
        return stringRedisTemplate.opsForValue().get(buildEntryKey(surveyId, userId));
    }

    public void clearEntryToken(Long surveyId, Long userId) {
        stringRedisTemplate.delete(buildEntryKey(surveyId, userId));
    }

    public boolean tryAcquireSubmitLock(Long surveyId, Long userId, Duration ttl) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(buildLockKey(surveyId, userId), "1", ttl);
        return Boolean.TRUE.equals(locked);
    }

    public void releaseSubmitLock(Long surveyId, Long userId) {
        stringRedisTemplate.delete(buildLockKey(surveyId, userId));
    }

    public long getOrInitQuotaCount(Long surveyId, LongSupplier fallbackLoader) {
        String key = buildQuotaKey(surveyId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (current != null && !current.isBlank()) {
            return parseLong(current, 0L);
        }
        long initValue = fallbackLoader.getAsLong();
        Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initValue));
        if (Boolean.TRUE.equals(created)) {
            return initValue;
        }
        String latest = stringRedisTemplate.opsForValue().get(key);
        return parseLong(latest, initValue);
    }

    public long incrementQuotaCount(Long surveyId) {
        Long value = stringRedisTemplate.opsForValue().increment(buildQuotaKey(surveyId));
        return value == null ? 0L : value;
    }

    public void resetQuotaCount(Long surveyId, long value) {
        stringRedisTemplate.opsForValue().set(buildQuotaKey(surveyId), String.valueOf(value));
    }

    private static String buildEntryKey(Long surveyId, Long userId) {
        return ENTRY_PREFIX + surveyId + ":" + userId;
    }

    private static String buildLockKey(Long surveyId, Long userId) {
        return LOCK_PREFIX + surveyId + ":" + userId;
    }

    private static String buildQuotaKey(Long surveyId) {
        return QUOTA_PREFIX + surveyId;
    }

    private static long parseLong(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception e) {
            return fallback;
        }
    }
}
