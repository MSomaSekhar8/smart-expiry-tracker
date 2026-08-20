package com.pantrytracker.auth;

import com.pantrytracker.common.ApiResponse;
import com.pantrytracker.common.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.TokenPair> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.allowRegister(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthDtos.TokenPair login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                    HttpServletRequest httpRequest) {
        if (!rateLimiter.allowLogin(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenPair refresh(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                      HttpServletRequest httpRequest) {
        if (!rateLimiter.allowRefresh(AuthRateLimiter.clientIp(httpRequest))) {
            throw new TooManyRequestsException();
        }
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(UUID.fromString(principal.id()));
    }

    @PostMapping("/logout")
    public ApiResponse logout() {
        // Stateless JWT — the client simply drops its tokens.
        return new ApiResponse("Logged out");
    }
}