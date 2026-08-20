package com.pantrytracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @Email(message = "A valid email address is required")
            @NotBlank(message = "Email is required")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @Size(max = 100, message = "Display name is too long")
            String displayName) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            String email,

            @NotBlank(message = "Password is required")
            String password) {}

    /**
     * The JSON body returned by login/register/refresh. Only the access token
     * reaches the client; the refresh token travels in an HttpOnly cookie.
     */
    public record TokenResponse(
            String accessToken,
            UserView user) {}

    /**
     * Service-internal result. The refresh token is handed to the controller
     * (which writes it into a cookie) and never serialized to JSON.
     */
    public record AuthTokens(
            String accessToken,
            String refreshToken,
            UserView user) {}

    public record UserView(
            UUID id,
            String email,
            String displayName,
            String role) {}
}