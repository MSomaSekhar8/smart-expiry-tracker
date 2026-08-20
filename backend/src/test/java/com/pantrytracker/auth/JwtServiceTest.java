package com.pantrytracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-longer-than-32-bytes-for-hs256";
    private static final String OTHER_SECRET = "another-different-secret-that-is-longer-than-32-bytes";

    private JwtService jwtService() {
        return new JwtService(SECRET, 60, 14);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String signedToken(UUID subject, String type, Instant expiry) {
        return Jwts.builder()
                .subject(subject.toString())
                .claim("typ", type)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    @Test
    void validAccessTokenParses() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        String token = service.createAccessToken(userId);

        assertThat(service.parseAccessToken(token)).isEqualTo(userId);
    }

    @Test
    void accessTokenExpiresAfterConfiguredTtl() throws Exception {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();
        String token = service.createAccessToken(userId);

        // The token's exp must be ~60 minutes in the future — that is when
        // it stops being accepted. Generate a token with the same subject but
        // an already-past expiration to prove expired tokens are rejected.
        String expired = signedToken(userId, "access", Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(service.parseAccessToken(token)).isEqualTo(userId);
        assertThatThrownBy(() -> service.parseAccessToken(expired))
                .isInstanceOf(Exception.class);
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        String refresh = service.createRefreshToken(userId, 4);

        assertThat(service.parseRefreshToken(refresh).userId()).isEqualTo(userId);
        assertThatThrownBy(() -> service.parseAccessToken(refresh))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshTokenCarriesItsGeneration() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        String refresh = service.createRefreshToken(userId, 7);

        JwtService.RefreshClaims claims = service.parseRefreshToken(refresh);
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.generation()).isEqualTo(7);
    }

    @Test
    void refreshTokenWithoutGenerationIsRejected() throws Exception {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();
        String noGeneration = Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(14, ChronoUnit.DAYS)))
                .signWith(key())
                .compact();

        assertThatThrownBy(() -> service.parseRefreshToken(noGeneration))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessTokenIsRejectedAsRefreshToken() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        String access = service.createAccessToken(userId);

        assertThatThrownBy(() -> service.parseRefreshToken(access))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();
        Key wrongKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.MINUTES)))
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> service.parseAccessToken(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = jwtService();
        String token = service.createAccessToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + flipMiddle(parts[1]) + "." + parts[2];

        assertThatThrownBy(() -> service.parseAccessToken(tampered))
                .isInstanceOf(Exception.class);
    }

    private static String flipMiddle(String value) {
        int mid = value.length() / 2;
        char flipped = value.charAt(mid) == 'A' ? 'B' : 'A';
        return value.substring(0, mid) + flipped + value.substring(mid + 1);
    }

    @Test
    void expiringSoonClaimCannotOverrideExpiration() {
        // A token whose exp is in the past must be rejected even if the
        // attacker re-signed nothing — parseSignedClaims enforces exp.
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();
        String expired = signedToken(userId, "access", Instant.now().minusSeconds(5));

        assertThatThrownBy(() -> service.parseAccessToken(expired))
                .isInstanceOf(Exception.class);
    }

    @Test
    void blankSecretFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtService("   ", 60, 14))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(null, 60, 14))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecretFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtService("too-short", 60, 14))
                .isInstanceOf(Exception.class);
    }

    @Test
    void accessTokenContainsOnlyUserIdAndPurposeClaim() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();
        String token = service.createAccessToken(userId);

        var claims = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("typ")).isEqualTo("access");
        assertThat(claims.get("email")).isNull();
        assertThat(claims.get("role")).isNull();
    }
}