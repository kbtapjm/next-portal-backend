package com.nextportal.admin;

import com.nextportal.common.member.entity.MemberStatus;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminIntegrationTest extends AdminTestSupport {

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

    @Test void swaggerDocumentsAuthenticationAndRecordSchemas() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.withCredentials").value(true));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/admin/auth/login'].post.summary").value("관리자 로그인"))
            .andExpect(jsonPath("$.paths['/api/admin/auth/login'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/admin/auth/refresh'].post.security[0].refreshCookie").isArray())
            .andExpect(jsonPath("$.paths['/api/admin/users'].get.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/admin/users'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/admin/users'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/admin/auth/logout'].post.responses['204']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.refreshCookie.name").value("portal_admin_refresh"))
            .andExpect(jsonPath("$.components.schemas.MemberView.properties.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.MemberPage.properties.content").exists())
            .andExpect(jsonPath("$.components.schemas.MemberView.properties.permissions").exists())
            .andExpect(jsonPath("$.paths['/api/admin/roles'].post.operationId").value("createRole"))
            .andExpect(jsonPath("$.paths['/api/admin/users/{id}/roles'].put.operationId").value("assignUserRoles"))
            .andExpect(jsonPath("$.components.schemas.CreateUser.properties.password.writeOnly").value(true));
    }

    @Test void swaggerOriginCanLoginButUntrustedOriginsStayBlocked() throws Exception {
        mvc.perform(post("/api/admin/auth/login").header("Origin", "http://localhost:8080")
            .contentType(MediaType.APPLICATION_JSON).content(loginBody("admin@example.com", PASSWORD)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/login").header("Origin", "https://attacker.example")
            .contentType(MediaType.APPLICATION_JSON).content(loginBody("admin@example.com", PASSWORD)))
            .andExpect(status().isForbidden());
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
        member.update(member.getName(), MemberStatus.DISABLED);
        members.saveAndFlush(member);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN)
            .cookie(signedIn.getResponse().getCookie("portal_admin_refresh"))).andExpect(status().isUnauthorized());
        member("user@example.com", "USER");
        mvc.perform(post("/api/admin/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("user@example.com", PASSWORD))).andExpect(status().isUnauthorized());
    }

}
