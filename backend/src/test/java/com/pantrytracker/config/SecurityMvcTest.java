package com.pantrytracker.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.pantrytracker.auth.AuthenticatedUser;
import com.pantrytracker.auth.AuthController;
import com.pantrytracker.auth.AuthDtos;
import com.pantrytracker.auth.AuthRateLimiter;
import com.pantrytracker.auth.AuthService;
import com.pantrytracker.auth.JwtService;
import com.pantrytracker.auth.RefreshCookieService;
import com.pantrytracker.common.HealthController;
import com.pantrytracker.common.TooManyRequestsException;
import com.pantrytracker.item.ItemController;
import com.pantrytracker.item.ItemService;
import com.pantrytracker.notification.DigestController;
import com.pantrytracker.notification.ExpiryDigestService;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogController;
import com.pantrytracker.wastelog.WasteLogRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({ItemController.class, WasteLogController.class, HealthController.class,
        DigestController.class, AuthController.class})
@Import({SecurityConfig.class, CorsConfig.class, AuthRateLimiter.class, RefreshCookieService.class})
class SecurityMvcTest {

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

    private User userWithId(String email) {
        User user = new User(email, "hash", "Test");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private UsernamePasswordAuthenticationToken authed(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities());
    }

    @Test
    void healthEndpointIsPublicAndMinimal() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unauthenticatedApiRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedRequestBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/items")
                        .with(authentication(authed(userWithId("a@example.com"))))
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void adminEndpointRejectsNormalUser() throws Exception {
        mockMvc.perform(post("/api/admin/digest/test")
                        .with(user("normal-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsAdmin() throws Exception {
        when(digestService.run())
                .thenReturn(new ExpiryDigestService.DigestReport(2, 1));

        mockMvc.perform(post("/api/admin/digest/test")
                        .with(user("admin-user").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiringSoonCount").value(2))
                .andExpect(jsonPath("$.expiredCount").value(1));
    }

    @Test
    void accessingAnotherUsersItemReturns403() throws Exception {
        User userA = userWithId("a@example.com");
        UUID itemB = UUID.randomUUID();
        when(itemService.get(eq(userA.getId()), eq(itemB)))
                .thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(get("/api/items/{id}", itemB)
                        .with(authentication(authed(userA))))
                .andExpect(status().isForbidden());
    }

    @Test
    void markingAnotherUsersItemWastedReturns403() throws Exception {
        User userA = userWithId("a@example.com");
        UUID itemB = UUID.randomUUID();
        when(itemService.markWasted(eq(userA.getId()), eq(itemB), any(), any()))
                .thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(post("/api/items/{id}/waste", itemB)
                        .with(authentication(authed(userA)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wasteLogEndpointIsScopedToAuthenticatedUserAndKeepsSnapshot() throws Exception {
        User userA = userWithId("a@example.com");
        WasteLog log = new WasteLog(userA, null, new BigDecimal("5"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(log, "itemName", "Rice");
        ReflectionTestUtils.setField(log, "unit", "kg");
        when(wasteLogRepository.findByUserIdOrderByLoggedAtDesc(eq(userA.getId()), any(Pageable.class)))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/waste-log")
                        .with(authentication(authed(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemName").value("Rice"))
                .andExpect(jsonPath("$[0].unit").value("kg"))
                .andExpect(jsonPath("$[0].quantityWasted").value(5))
                .andExpect(jsonPath("$[0].userId").value(userA.getId().toString()));

        verify(wasteLogRepository)
                .findByUserIdOrderByLoggedAtDesc(eq(userA.getId()), any(Pageable.class));
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthDtos.AuthTokens("access", "refresh",
                        new AuthDtos.UserView(UUID.randomUUID(), "a@example.com", null, "USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedMeReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerOnMeReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedMeReturnsTheCurrentUser() throws Exception {
        User user = userWithId("me@example.com");
        when(authService.me(any()))
                .thenReturn(new AuthDtos.UserView(user.getId(), "me@example.com", "Me", "USER"));

        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(authed(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.displayName").value("Me"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void optionsPreflightIsPermitted() throws Exception {
        mockMvc.perform(options("/api/items")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void itemsEndpointRequiresAuthenticationForWrites() throws Exception {
        mockMvc.perform(post("/api/items")
                        .contentType("application/json")
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiRouteReturns404WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .with(user("u").roles("USER")))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}");
    }

    private MockHttpServletRequestBuilder register(String ip) {
        return post("/api/auth/register")
                .header("X-Forwarded-For", ip)
                .contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}");
    }

    private MockHttpServletRequestBuilder refresh(String ip) {
        return post("/api/auth/refresh")
                .header("X-Forwarded-For", ip)
                .cookie(new Cookie(RefreshCookieService.COOKIE_NAME, "cookie-token"));
    }

    private MockHttpServletRequestBuilder logout(String ip) {
        return post("/api/auth/logout")
                .header("X-Forwarded-For", ip)
                .cookie(new Cookie(RefreshCookieService.COOKIE_NAME, "cookie-token"));
    }

    private AuthDtos.AuthTokens authTokens() {
        return new AuthDtos.AuthTokens("access", "refresh",
                new AuthDtos.UserView(UUID.randomUUID(), "a@example.com", null, "USER"));
    }

    @Test
    void loginRateLimitsTheSixthAttemptPerIpWithGenericMessage() throws Exception {
        when(authService.login(any())).thenReturn(authTokens());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("10.1.0.1")).andExpect(status().isOk());
        }
        mockMvc.perform(login("10.1.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(TooManyRequestsException.MESSAGE));
        verify(authService, times(5)).login(any());
    }

    @Test
    void registerRateLimitsTheFourthAttemptPerIp() throws Exception {
        when(authService.register(any())).thenReturn(authTokens());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(register("10.1.0.2")).andExpect(status().isCreated());
        }
        mockMvc.perform(register("10.1.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(TooManyRequestsException.MESSAGE));
        verify(authService, times(3)).register(any());
    }

    @Test
    void refreshRateLimitsTheEleventhAttemptPerIp() throws Exception {
        when(authService.refresh(any())).thenReturn(authTokens());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(refresh("10.1.0.3")).andExpect(status().isOk());
        }
        mockMvc.perform(refresh("10.1.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(TooManyRequestsException.MESSAGE));
        verify(authService, times(10)).refresh(any());
    }

    @Test
    void rateLimitsAreIndependentPerIp() throws Exception {
        when(authService.login(any())).thenReturn(authTokens());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("10.1.0.4")).andExpect(status().isOk());
        }
        mockMvc.perform(login("10.1.0.4")).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("10.1.0.5")).andExpect(status().isOk());
    }

    @Test
    void rateLimitingDoesNotAffectHealthOrAuthenticatedEndpoints() throws Exception {
        when(authService.login(any())).thenReturn(authTokens());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("10.1.0.6")).andExpect(status().isOk());
        }
        mockMvc.perform(login("10.1.0.6")).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/health")).andExpect(status().isOk());

        User user = userWithId("b@example.com");
        when(itemService.list(eq(user.getId()), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/items").with(authentication(authed(user))))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCredentialsStillReturnTheExistingGenericError() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(login("10.1.0.7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookieOnly() throws Exception {
        when(authService.login(any())).thenReturn(authTokens());

        mockMvc.perform(login("10.1.0.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("a@example.com"))
                .andExpect(cookie().value(RefreshCookieService.COOKIE_NAME, "refresh"))
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true))
                .andExpect(cookie().secure(RefreshCookieService.COOKIE_NAME, false))
                .andExpect(cookie().path(RefreshCookieService.COOKIE_NAME, "/api/auth"))
                .andExpect(cookie().maxAge(RefreshCookieService.COOKIE_NAME, 14 * 24 * 60 * 60))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    void registerAlsoIssuesTheRefreshCookie() throws Exception {
        when(authService.register(any())).thenReturn(authTokens());

        mockMvc.perform(register("10.1.0.9"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(RefreshCookieService.COOKIE_NAME, "refresh"))
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true));
    }

    @Test
    void refreshReadsTheCookieAndIssuesANewAccessTokenAndCookie() throws Exception {
        when(authService.refresh("cookie-token"))
                .thenReturn(new AuthDtos.AuthTokens("new-access", "new-refresh",
                        new AuthDtos.UserView(UUID.randomUUID(), "a@example.com", null, "USER")));

        mockMvc.perform(refresh("10.1.0.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value(RefreshCookieService.COOKIE_NAME, "new-refresh"))
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true));

        verify(authService).refresh("cookie-token");
    }

    @Test
    void refreshWithoutCookieReturns401WithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "10.1.0.11"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));

        verify(authService, never()).refresh(any());
    }

    @Test
    void refreshWithInvalidCookieReturns401() throws Exception {
        when(authService.refresh("cookie-token"))
                .thenThrow(new BadCredentialsException("Invalid or expired refresh token"));

        mockMvc.perform(refresh("10.1.0.12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    void logoutClearsTheRefreshCookieWithMatchingAttributes() throws Exception {
        mockMvc.perform(logout("10.1.0.13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out"))
                .andExpect(cookie().value(RefreshCookieService.COOKIE_NAME, ""))
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true))
                .andExpect(cookie().path(RefreshCookieService.COOKIE_NAME, "/api/auth"))
                .andExpect(cookie().maxAge(RefreshCookieService.COOKIE_NAME, 0))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));

        verify(authService).revokeRefreshToken("cookie-token");
    }

    @Test
    void logoutWithoutCookieStillClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Forwarded-For", "10.1.0.14"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(RefreshCookieService.COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(RefreshCookieService.COOKIE_NAME, 0));

        verify(authService, never()).revokeRefreshToken(any());
    }
}