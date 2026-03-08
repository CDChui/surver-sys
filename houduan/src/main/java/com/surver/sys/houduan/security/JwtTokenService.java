package com.surver.sys.houduan.security;

import com.surver.sys.houduan.common.ErrorCode;
import com.surver.sys.houduan.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(normalizeSecret(jwtProperties.getSecret()));
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.getTtlSeconds());
        String jti = principal.jti() == null || principal.jti().isBlank() ? UUID.randomUUID().toString() : principal.jti();

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(principal.userId()))
                .claim("uid", principal.uid())
                .claim("loginName", principal.username())
                .claim("displayName", principal.displayName())
                .claim("role", principal.role())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public UserPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = parseLong(claims.getSubject());
            String uid = claims.get("uid", String.class);
            String loginName = claims.get("loginName", String.class);
            String displayName = claims.get("displayName", String.class);
            String role = claims.get("role", String.class);
            String jti = claims.getId();
            return new UserPrincipal(userId, uid, loginName, displayName, role, jti);
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED_OR_REVOKED, "token 已过期");
        } catch (SecurityException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token 无效");
        }
    }

    public long getRemainingSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long nowMs = System.currentTimeMillis();
            long expMs = claims.getExpiration().getTime();
            long remain = Math.max(0, (expMs - nowMs) / 1000);
            return remain;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (Exception e) {
            return -1L;
        }
    }

    private static byte[] normalizeSecret(String rawSecret) {
        String secret = rawSecret == null ? "" : rawSecret;
        if (secret.length() < 32) {
            secret = (secret + "00000000000000000000000000000000").substring(0, 32);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
