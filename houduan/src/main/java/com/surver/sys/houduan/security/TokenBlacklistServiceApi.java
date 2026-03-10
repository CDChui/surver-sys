package com.surver.sys.houduan.security;

public interface TokenBlacklistServiceApi {

    void blacklist(String jti, long ttlSeconds);

    boolean isBlacklisted(String jti);
}
