package com.pantrytracker.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 JWTs. The {@code typ} claim distinguishes
 * access tokens (typ=access) from refresh tokens (typ=refresh) so a refresh
 * token can never be used as a bearer credential.
 */
@Service
public class JwtService {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes,
                      @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured — refusing to start with an empty secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String createAccessToken(UUID userId) {
        return createToken(userId, TYPE_ACCESS, accessTtlMinutes, ChronoUnit.MINUTES);
    }

    public String createRefreshToken(UUID userId) {
        return createToken(userId, TYPE_REFRESH, refreshTtlDays, ChronoUnit.DAYS);
    }

    private String createToken(UUID userId, String type, long ttl, ChronoUnit unit) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl, unit)))
                .signWith(key)
                .compact();
    }

    /** @return the subject (user id) when the token is a valid, unexpired access token. */
    public UUID parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!TYPE_ACCESS.equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Not an access token");
        }
        return UUID.fromString(claims.getSubject());
    }

    /** @return the subject (user id) when the token is a valid, unexpired refresh token. */
    public UUID parseRefreshToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Not a refresh token");
        }
        return UUID.fromString(claims.getSubject());
    }
}