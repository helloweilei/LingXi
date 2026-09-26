package com.lingxi.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtService {

    /** application.yml 中的默认密钥，仅允许开发环境使用 */
    private static final String DEFAULT_SECRET = "lingxi-creation-jwt-secret-2026-please-change-me-0123456789abcdef";

    private final SecretKey key;
    private final long expireHours;

    public JwtService(@Value("${lingxi.jwt.secret}") String secret,
                      @Value("${lingxi.jwt.expire-hours}") long expireHours,
                      Environment environment) {
        if (DEFAULT_SECRET.equals(secret) && isProd(environment)) {
            throw new IllegalStateException("生产环境禁止使用默认 JWT 密钥，请通过环境变量 LINGXI_JWT_SECRET 配置至少 32 字节的随机串");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    private static boolean isProd(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    public String issue(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireHours * 3600_000L))
                .signWith(key)
                .compact();
    }

    /** 校验失败返回 null，由过滤器决定放行为匿名 */
    public String parseUsername(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
