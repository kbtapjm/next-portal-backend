package com.nextportal.admin;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RolePermissionIntegrationTest extends AdminTestSupport {
    @Test void viewerCanReadButCannotWriteOrManageRoles() throws Exception {
        member("viewer@example.com", "USER_VIEWER");
        var viewer = token(signIn("viewer@example.com"));
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + viewer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("USER_VIEWER"))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.containsInAnyOrder("admin:access", "user:read")));
        mvc.perform(get("/api/admin/users").param("roleCode", "USER_VIEWER").header("Authorization", "Bearer " + viewer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + viewer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(newUser("created@example.com", List.of("USER"))))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + viewer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + viewer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/roles/USER_MANAGER").header("Authorization", "Bearer " + viewer)).andExpect(status().isForbidden());
    }

    @Test void userWriterCannotAssignRolesOrModifyPermissionAdministrators() throws Exception {
        member("writer@example.com", "USER_MANAGER");
        var writer = token(signIn("writer@example.com"));
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(newUser("ordinary@example.com", List.of("USER"))))).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(newUser("privileged@example.com", List.of("USER_VIEWER"))))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "root@example.com", "name", "사용자", "password", PASSWORD, "role", "ADMIN"))))
            .andExpect(status().isForbidden());
        var ordinary = members.findByEmail("ordinary@example.com").orElseThrow();
        mvc.perform(put("/api/admin/users/" + ordinary.getId()).header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경\",\"status\":\"DISABLED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/" + ordinary.getId()).header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경\",\"status\":\"ACTIVE\",\"roles\":[\"ADMIN\"]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + ordinary.getId() + "/roles").header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"ADMIN\"]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + writer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경\",\"status\":\"DISABLED\"}")).andExpect(status().isForbidden());
    }

    @Test void multiRoleUnionAndAssignmentRevocationAreEnforced() throws Exception {
        var root = token(signIn());
        createRole(root, "TEST_ENTRY", "admin:access");
        createRole(root, "TEST_READ", "user:read");
        var target = member("combined@example.com", "TEST_ENTRY", "TEST_READ");
        var before = signIn("combined@example.com");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token(before))).andExpect(status().isOk());
        // A member with two matching roles must appear only once, and count/filters must agree.
        mvc.perform(get("/api/admin/users").param("keyword", "combined").param("role", "ADMIN").param("size", "1")
            .header("Authorization", "Bearer " + root)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"TEST_ENTRY\"]}")).andExpect(status().isOk());
        assertSessionRejected(before);
        var after = token(signIn("combined@example.com"));
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + after)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + after)).andExpect(status().isForbidden());
    }

    @Test void sharedPermissionChangesRevokeEverySessionAndNeverResurrectOldTokens() throws Exception {
        var root = token(signIn());
        createRole(root, "TEST_AUDITOR", "admin:access", "user:read");
        member("first@example.com", "TEST_AUDITOR");
        member("second@example.com", "TEST_AUDITOR");
        var first = signIn("first@example.com");
        var second = signIn("second@example.com");
        updateRole(root, "TEST_AUDITOR", "이름만 변경", "admin:access", "user:read");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token(first))).andExpect(status().isOk());
        updateRole(root, "TEST_AUDITOR", "조회 회수", "admin:access");
        assertSessionRejected(first);
        assertSessionRejected(second);
        var reduced = token(signIn("first@example.com"));
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + reduced)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + reduced)).andExpect(status().isForbidden());
        updateRole(root, "TEST_AUDITOR", "조회 복원", "admin:access", "user:read");
        assertSessionRejected(first);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token(signIn("first@example.com"))))
            .andExpect(status().isOk());
    }

    @Test void rolesHaveValidatedCatalogAndProtectedLifecycle() throws Exception {
        var root = token(signIn());
        mvc.perform(get("/api/admin/permissions").header("Authorization", "Bearer " + root)).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.hasItem("role:manage")));
        createRole(root, "TEST_LIFECYCLE", "admin:access");
        mvc.perform(get("/api/admin/roles/TEST_LIFECYCLE").header("Authorization", "Bearer " + root)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"TEST_LIFECYCLE\",\"name\":\"중복\",\"permissions\":[]}")).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"TEST_UNKNOWN\",\"name\":\"알 수 없음\",\"permissions\":[\"unknown:permission\"]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/roles/ADMIN").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"변경\",\"permissions\":[]}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/roles/USER").header("Authorization", "Bearer " + root)).andExpect(status().isConflict());
        var target = member("assigned@example.com", "TEST_LIFECYCLE");
        mvc.perform(delete("/api/admin/roles/TEST_LIFECYCLE").header("Authorization", "Bearer " + root)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"USER\"]}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/roles/TEST_LIFECYCLE").header("Authorization", "Bearer " + root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/roles/TEST_LIFECYCLE").header("Authorization", "Bearer " + root)).andExpect(status().isNotFound());
    }

    @Test void selfChangesAndLastActiveAdministratorAreProtected() throws Exception {
        var root = token(signIn());
        createRole(root, "TEST_AUTH_MANAGER", "admin:access", "role:manage", "user:write");
        var manager = member("authority@example.com", "TEST_AUTH_MANAGER");
        var managerToken = token(signIn("authority@example.com"));
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/roles").header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"USER\"]}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/" + admin.getId()).header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"관리자\",\"status\":\"DISABLED\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/" + manager.getId() + "/roles").header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"ADMIN\"]}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/roles/TEST_AUTH_MANAGER").header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"본인 권한 변경\",\"permissions\":[]}")).andExpect(status().isConflict());
        member("backup@example.com", "ADMIN");
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/roles").header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"USER\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + root)).andExpect(status().isUnauthorized());
    }

    @Test void legacyEditsPreserveCustomRolesAndInvalidAssignmentsAreRejected() throws Exception {
        var root = token(signIn());
        var target = member("legacy@example.com", "USER_VIEWER");
        var session = signIn("legacy@example.com");
        mvc.perform(put("/api/admin/users/" + target.getId()).header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"이름 수정\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER_VIEWER"));
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token(session))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[null]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":[\"UNKNOWN\"]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "ambiguous@example.com", "name", "사용자", "password", PASSWORD, "role", "ADMIN", "roles", List.of("USER")))))
            .andExpect(status().isBadRequest());
    }

    @Test void concurrentDemotionsCannotRemoveBothRemainingAdministrators() throws Exception {
        var root = token(signIn());
        createRole(root, "TEST_CONCURRENT_MANAGER", "admin:access", "role:manage");
        member("concurrent@example.com", "TEST_CONCURRENT_MANAGER");
        var manager = token(signIn("concurrent@example.com"));
        var backup = member("backup@example.com", "ADMIN");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = List.of(admin.getId(), backup.getId()).stream().map(id -> executor.submit(() ->
                mvc.perform(put("/api/admin/users/" + id + "/roles").header("Authorization", "Bearer " + manager)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                    .andReturn().getResponse().getStatus())).toList();
            assertThat(List.of(requests.get(0).get(10, TimeUnit.SECONDS), requests.get(1).get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(members.countByStatusAndRoles_Code(com.nextportal.common.member.entity.MemberStatus.ACTIVE, "ADMIN")).isEqualTo(1);
    }

    private Map<String, Object> newUser(String email, List<String> assigned) {
        return Map.of("email", email, "name", "사용자", "password", PASSWORD, "roles", assigned);
    }
    private void createRole(String root, String code, String... permissions) throws Exception {
        mvc.perform(post("/api/admin/roles").header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", code, "name", code, "permissions", List.of(permissions)))))
            .andExpect(status().isCreated());
    }
    private void updateRole(String root, String code, String name, String... permissions) throws Exception {
        mvc.perform(put("/api/admin/roles/" + code).header("Authorization", "Bearer " + root).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("name", name, "permissions", List.of(permissions)))))
            .andExpect(status().isOk());
    }
    private void assertSessionRejected(MvcResult signedIn) throws Exception {
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token(signedIn))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN).cookie(signedIn.getResponse().getCookie("portal_admin_refresh")))
            .andExpect(status().isUnauthorized());
    }
}
