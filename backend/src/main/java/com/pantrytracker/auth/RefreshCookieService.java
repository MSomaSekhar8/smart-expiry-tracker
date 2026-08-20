package com.pantrytracker.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Writes and clears the HttpOnly refresh-token cookie.
 *
 * <p>Cookie attributes are driven by configuration so local development over
 * plain HTTP keeps working while production enables {@code Secure}:
 * <ul>
 *   <li>{@code AUTH_COOKIE_SECURE} — MUST be {@code true} in production
 *       (HTTPS); defaults to {@code false} for localhost development.</li>
 *   <li>{@code AUTH_COOKIE_SAMESITE} — defaults to {@code Lax}, which works
 *       for local development (localhost:5173 → localhost:8080 is same-site)
 *       and for same-site production topologies (e.g. app.example.com →
 *       api.example.com). If the frontend and API are served from different
 *       registrable domains, set it to {@code None} (which requires
 *       {@code AUTH_COOKIE_SECURE=true}).</li>
 * </ul>
 *
 * <p>The path is scoped to the auth namespace so the browser sends the cookie
 * to the refresh and logout endpoints only.
 */
@Component
public class RefreshCookieService {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;
    private final long maxAgeSeconds;

    public RefreshCookieService(
            @Value("${app.auth.cookie-secure:false}") boolean secure,
            @Value("${app.auth.cookie-samesite:Lax}") String sameSite,
            @Value("${app.jwt.refresh-ttl-days:14}") long refreshTtlDays) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAgeSeconds = refreshTtlDays * 24 * 60 * 60;
    }

    public void addTo(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, maxAgeSeconds).toString());
    }

    /** Sends a same-name, same-path, expired cookie so the browser drops it. */
    public void clearFrom(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite(sameSite)
                .build();
    }
}