package com.pantrytracker.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pantrytracker.auth.AuthenticatedUser;
import com.pantrytracker.auth.AuthController;
import com.pantrytracker.auth.AuthDtos;
import com.pantrytracker.auth.AuthService;
import com.pantrytracker.auth.JwtService;
import com.pantrytracker.common.HealthController;
import com.pantrytracker.item.ItemController;
import com.pantrytracker.item.ItemService;
import com.pantrytracker.notification.DigestController;
import com.pantrytracker.notification.ExpiryDigestService;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogController;
import com.pantrytracker.wastelog.WasteLogRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ItemController.class, WasteLogController.class, HealthController.class,
        DigestController.class, AuthController.class})
@Import({SecurityConfig.class, CorsConfig.class})
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
                .thenReturn(new AuthDtos.TokenPair("access", "refresh",
                        new AuthDtos.UserView(UUID.randomUUID(), "a@example.com", null, "USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
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
}