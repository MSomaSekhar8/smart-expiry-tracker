package com.pantrytracker.auth;

import com.pantrytracker.common.BadRequestException;
import com.pantrytracker.common.ConflictException;
import com.pantrytracker.common.NotFoundException;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthTokens register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.displayName() == null ? null : request.displayName().trim());
        userRepository.save(user);
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthTokens login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokensFor(user);
    }

    /**
     * Validates a refresh token, then rotates it: the user's generation is
     * bumped so the presented token can never be used again. The lock on the
     * user row serializes concurrent refreshes so a token cannot be replayed
     * twice by racing two requests.
     */
    @Transactional
    public AuthDtos.AuthTokens refresh(String refreshToken) {
        JwtService.RefreshClaims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        User user = userRepository.findByIdForUpdate(claims.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        if (user.getRefreshGeneration() != claims.generation()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        user.setRefreshGeneration(user.getRefreshGeneration() + 1);
        return tokensFor(user);
    }

    /**
     * Revokes every outstanding refresh token for the user behind the given
     * token by bumping their generation. Invalid or expired tokens are
     * ignored — the caller clears the cookie regardless.
     */
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        try {
            JwtService.RefreshClaims claims = jwtService.parseRefreshToken(refreshToken);
            userRepository.findByIdForUpdate(claims.userId()).ifPresent(user ->
                    user.setRefreshGeneration(user.getRefreshGeneration() + 1));
        } catch (Exception ignored) {
            // nothing to revoke — the cookie is cleared by the controller anyway
        }
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toView(user);
    }

    private AuthDtos.AuthTokens tokensFor(User user) {
        return new AuthDtos.AuthTokens(
                jwtService.createAccessToken(user.getId()),
                jwtService.createRefreshToken(user.getId(), user.getRefreshGeneration()),
                toView(user));
    }

    private AuthDtos.UserView toView(User user) {
        return new AuthDtos.UserView(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getRole().name());
    }
}