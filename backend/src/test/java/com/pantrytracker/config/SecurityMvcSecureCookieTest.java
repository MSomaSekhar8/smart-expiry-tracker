package com.pantrytracker.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.pantrytracker.auth.AuthController;
import com.pantrytracker.auth.AuthDtos;
import com.pantrytracker.auth.AuthRateLimiter;
import com.pantrytracker.auth.AuthService;
import com.pantrytracker.auth.JwtService;
import com.pantrytracker.auth.RefreshCookieService;
import com.pantrytracker.common.HealthController;
import com.pantrytracker.item.ItemController;
import com.pantrytracker.item.ItemService;
import com.pantrytracker.notification.DigestController;
import com.pantrytracker.notification.ExpiryDigestService;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLogController;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the refresh cookie is Secure when the production cookie
 * configuration is enabled — the property AUTH_COOKIE_SECURE=true that
 * production deployments MUST set.
 */
@WebMvcTest({ItemController.class, WasteLogController.class, HealthController.class,
        DigestController.class, AuthController.class})
@Import({SecurityConfig.class, CorsConfig.class, AuthRateLimiter.class, RefreshCookieService.class})
@TestPropertySource(properties = "app.auth.cookie-secure=true")
class SecurityMvcSecureCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private ItemService itemService;
    @MockBean
    private WasteLogRepository wasteLogRepository;
    @MockBean
    private ExpiryDigestService digestService;
    @MockBean
    private AuthService authService;

    @Test
    void refreshCookieIsSecureWhenProductionCookieConfigIsEnabled() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthDtos.AuthTokens("access", "refresh",
                        new AuthDtos.UserView(UUID.randomUUID(), "a@example.com", null, "USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(cookie().secure(RefreshCookieService.COOKIE_NAME, true))
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }
}