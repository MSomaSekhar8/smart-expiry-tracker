package com.pantrytracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    private User user(long generation) {
        User user = new User("a@example.com", "hash", "Test");
        user.setRefreshGeneration(generation);
        return user;
    }

    @Test
    void loginReturnsTokensForValidCredentials() {
        User user = user(2);
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(jwtService.createAccessToken(user.getId())).thenReturn("access-token");
        when(jwtService.createRefreshToken(user.getId(), 2)).thenReturn("refresh-token");

        AuthDtos.AuthTokens tokens = authService.login(
                new AuthDtos.LoginRequest("A@Example.com", "secret123"));

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.user().email()).isEqualTo("a@example.com");
        verify(jwtService).createRefreshToken(user.getId(), 2);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = user(0);
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("a@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshWithMatchingGenerationRotatesTheToken() {
        User user = user(3);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.parseRefreshToken("old-refresh"))
                .thenReturn(new JwtService.RefreshClaims(user.getId(), 3));
        when(jwtService.createAccessToken(user.getId())).thenReturn("new-access");
        when(jwtService.createRefreshToken(user.getId(), 4)).thenReturn("new-refresh");

        AuthDtos.AuthTokens tokens = authService.refresh("old-refresh");

        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
        assertThat(user.getRefreshGeneration()).isEqualTo(4);
        verify(jwtService).createRefreshToken(user.getId(), 4);
    }

    @Test
    void refreshWithStaleGenerationIsRejected() {
        User user = user(5);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.parseRefreshToken("old-refresh"))
                .thenReturn(new JwtService.RefreshClaims(user.getId(), 4));

        assertThatThrownBy(() -> authService.refresh("old-refresh"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid or expired refresh token");
        assertThat(user.getRefreshGeneration()).isEqualTo(5);
    }

    @Test
    void refreshWithInvalidTokenIsRejected() {
        when(jwtService.parseRefreshToken("garbage")).thenThrow(new IllegalArgumentException());

        assertThatThrownBy(() -> authService.refresh("garbage"))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void refreshWithMissingUserIsRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());
        when(jwtService.parseRefreshToken("token"))
                .thenReturn(new JwtService.RefreshClaims(userId, 0));

        assertThatThrownBy(() -> authService.refresh("token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void revokeRefreshTokenBumpsTheGeneration() {
        User user = user(6);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.parseRefreshToken("stolen-token"))
                .thenReturn(new JwtService.RefreshClaims(user.getId(), 6));

        authService.revokeRefreshToken("stolen-token");

        assertThat(user.getRefreshGeneration()).isEqualTo(7);
    }

    @Test
    void revokeRefreshTokenIgnoresInvalidTokens() {
        when(jwtService.parseRefreshToken("garbage")).thenThrow(new IllegalArgumentException());

        authService.revokeRefreshToken("garbage");

        verify(userRepository, never()).findByIdForUpdate(any());
    }
}