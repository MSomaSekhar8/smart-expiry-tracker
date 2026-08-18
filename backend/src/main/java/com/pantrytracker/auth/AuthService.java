package com.pantrytracker.auth;

import com.pantrytracker.common.BadRequestException;
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
    public AuthDtos.TokenPair register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.displayName() == null ? null : request.displayName().trim());
        userRepository.save(user);
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenPair login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenPair refresh(AuthDtos.RefreshRequest request) {
        UUID userId;
        try {
            userId = jwtService.parseRefreshToken(request.refreshToken());
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toView(user);
    }

    private AuthDtos.TokenPair tokensFor(User user) {
        return new AuthDtos.TokenPair(
                jwtService.createAccessToken(user.getId()),
                jwtService.createRefreshToken(user.getId()),
                toView(user));
    }

    private AuthDtos.UserView toView(User user) {
        return new AuthDtos.UserView(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getRole().name());
    }
}