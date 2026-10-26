package com.nextportal.admin;

import com.nextportal.api.security.SessionStore;
import com.nextportal.common.member.*;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {
    private static final String PASSWORD = "test-password-123!";
    private static final String ORIGIN = "http://localhost:3000";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SessionStore sessions;
    final Map<String, SessionStore.Session> state = new ConcurrentHashMap<>();
    Member admin;

    @BeforeEach void setup() {
        members.deleteAll(); state.clear();
        admin = members.save(new Member("admin@example.com", "관리자", passwords.encode(PASSWORD), Role.ADMIN));
        doAnswer(call -> { state.put(call.getArgument(0), new SessionStore.Session(call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4))); return null; })
            .when(sessions).create(anyString(), anyLong(), anyLong(), anyString(), anyString(), any(Duration.class));
        when(sessions.find(anyString())).thenAnswer(call -> Optional.ofNullable(state.get(call.<String>getArgument(0))));
        when(sessions.rotate(anyString(), anyString(), anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            var sid = call.<String>getArgument(0);
            var previous = state.get(sid);
            return Optional.ofNullable(previous).filter(value -> value.refreshHash().equals(call.<String>getArgument(1)))
                .map(value -> { state.put(sid, new SessionStore.Session(value.memberId(), value.version(), call.getArgument(2), call.getArgument(3))); return true; }).orElse(false);
        });
        doAnswer(call -> { state.remove(call.<String>getArgument(0)); return null; }).when(sessions).delete(anyString());
    }

    @Test void authenticationRotationAndLogout() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/login").header("Origin", "https://attacker.example")
            .contentType(MediaType.APPLICATION_JSON).content(loginBody("admin@example.com", PASSWORD))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).content(loginBody("admin@example.com", "wrong-password"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).content(loginBody("missing@example.com", PASSWORD))).andExpect(status().isUnauthorized());
        var signedIn = signIn();
        var token = token(signedIn);
        var cookie = signedIn.getResponse().getCookie("portal_admin_refresh");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(signedIn.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
        var rotated = mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN).cookie(cookie))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        var nextToken = token(rotated);
        mvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + nextToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + nextToken)).andExpect(status().isUnauthorized());
    }

    @Test void userCrudQuerydslValidationAndSelfProtection() throws Exception {
        var token = token(signIn());
        var create = mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "email", "USER@example.com", "name", "테스트 사용자", "password", PASSWORD, "role", "USER"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        var id = json.readTree(create.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/admin/users").param("keyword", "테스트").param("role", "USER")
            .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/admin/users").param("size", "101").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경\",\"role\":\"USER\",\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"관리자\",\"role\":\"USER\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isConflict());
    }

    @Test void disablingAccountRevokesExistingTokensAndRegularUsersCannotLogin() throws Exception {
        var signedIn = signIn();
        var token = token(signedIn);
        var member = members.findById(admin.getId()).orElseThrow();
        member.update(member.getName(), Role.ADMIN, MemberStatus.DISABLED);
        members.saveAndFlush(member);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN)
            .cookie(signedIn.getResponse().getCookie("portal_admin_refresh"))).andExpect(status().isUnauthorized());
        members.save(new Member("user@example.com", "사용자", passwords.encode(PASSWORD), Role.USER));
        mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("user@example.com", PASSWORD))).andExpect(status().isUnauthorized());
    }

    private MvcResult signIn() throws Exception {
        return mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("admin@example.com", PASSWORD))).andExpect(status().isOk()).andReturn();
    }
    private String loginBody(String email, String password) throws Exception { return json.writeValueAsString(Map.of("email", email, "password", password)); }
    private String token(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText(); }
}
