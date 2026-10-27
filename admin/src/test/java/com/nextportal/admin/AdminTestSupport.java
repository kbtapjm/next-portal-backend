package com.nextportal.admin;

import com.nextportal.api.security.SessionStore;
import com.nextportal.common.authorization.RoleRepository;
import com.nextportal.common.member.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "portal.docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AdminTestSupport {
    static final String PASSWORD = "test-password-123!";
    static final String ORIGIN = "http://localhost:3000";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SessionStore sessions;
    final Map<String, SessionStore.Session> state = new ConcurrentHashMap<>();
    Member admin;

    @BeforeEach void setup() {
        members.deleteAll(); state.clear();
        roles.deleteAll(roles.findAll().stream().filter(value -> value.getCode().startsWith("TEST_")).toList());
        admin = member("admin@example.com", "ADMIN");
        doAnswer(call -> { state.put(call.getArgument(0), new SessionStore.Session(call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5))); return null; })
            .when(sessions).create(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), any(Duration.class));
        when(sessions.find(anyString())).thenAnswer(call -> Optional.ofNullable(state.get(call.<String>getArgument(0))));
        when(sessions.rotate(anyString(), anyString(), anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            var sid = call.<String>getArgument(0);
            var previous = state.get(sid);
            return Optional.ofNullable(previous).filter(value -> value.refreshHash().equals(call.<String>getArgument(1)))
                .map(value -> { state.put(sid, new SessionStore.Session(value.memberId(), value.version(), value.authorizationVersion(), call.getArgument(2), call.getArgument(3))); return true; }).orElse(false);
        });
        doAnswer(call -> { state.remove(call.<String>getArgument(0)); return null; }).when(sessions).delete(anyString());
    }

    Member member(String email, String... roleCodes) {
        return members.saveAndFlush(new Member(email, "테스트 사용자", passwords.encode(PASSWORD), roles.findAllById(List.of(roleCodes))));
    }
    MvcResult signIn() throws Exception { return signIn("admin@example.com"); }
    MvcResult signIn(String email) throws Exception {
        return mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(email, PASSWORD))).andExpect(status().isOk()).andReturn();
    }
    String loginBody(String email, String password) throws Exception { return json.writeValueAsString(Map.of("email", email, "password", password)); }
    String token(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText(); }
}
