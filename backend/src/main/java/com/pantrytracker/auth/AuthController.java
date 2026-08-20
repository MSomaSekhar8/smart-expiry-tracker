package com.pantrytracker.auth;

import com.pantrytracker.common.ApiResponse;
import com.pantrytracker.common.TooManyRequestsException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final RefreshCookieService refreshCookieService;

    public AuthController(AuthService authService,
                          AuthRateLimiter rateLimiter,
                          RefreshCookieService refreshCookieService) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.TokenResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (!rateLimiter.allowRegister(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        AuthDtos.AuthTokens tokens = authService.register(request);
        refreshCookieService.addTo(httpResponse, tokens.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthDtos.TokenResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        if (!rateLimiter.allowLogin(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        AuthDtos.AuthTokens tokens = authService.login(request);
        refreshCookieService.addTo(httpResponse, tokens.refreshToken());
        return new AuthDtos.TokenResponse(tokens.accessToken(), tokens.user());
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        if (!rateLimiter.allowRefresh(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        String refreshToken = refreshTokenFrom(httpRequest);
        if (refreshToken == null) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        AuthDtos.AuthTokens tokens = authService.refresh(refreshToken);
        refreshCookieService.addTo(httpResponse, tokens.refreshToken());
        return new AuthDtos.TokenResponse(tokens.accessToken(), tokens.user());
    }

    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(UUID.fromString(principal.id()));
    }

    @PostMapping("/logout")
    public ApiResponse logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = refreshTokenFrom(httpRequest);
        if (refreshToken != null) {
            // Best effort: revokes the user's refresh generation so any
            // outstanding (e.g. stolen, still in another cookie jar) refresh
            // token stops working. Failures are ignored — the cookie below is
            // cleared either way.
            authService.revokeRefreshToken(refreshToken);
        }
        refreshCookieService.clearFrom(httpResponse);
        return new ApiResponse("Logged out");
    }

    private String refreshTokenFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> RefreshCookieService.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}