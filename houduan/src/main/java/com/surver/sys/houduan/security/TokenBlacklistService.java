package com.surver.sys.houduan.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Profile("!nodeps")
public class TokenBlacklistService implements TokenBlacklistServiceApi {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exist = stringRedisTemplate.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(exist);
    }
}
