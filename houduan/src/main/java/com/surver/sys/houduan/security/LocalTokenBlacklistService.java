package com.surver.sys.houduan.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("nodeps")
public class LocalTokenBlacklistService implements TokenBlacklistServiceApi {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
            return;
        }
        blacklist.put(jti, Instant.now().plusSeconds(ttlSeconds).toEpochMilli());
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long expireAt = blacklist.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }
}
