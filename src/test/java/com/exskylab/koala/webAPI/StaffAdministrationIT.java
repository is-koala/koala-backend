package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffAdministrationIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'admin-staff-%@iskoala.com')");
        deleteStaffMembersLike("admin-staff-%@iskoala.com");
    }

    private String email() {
        return "admin-staff-" + UUID.randomUUID() + "@iskoala.com";
    }

    private UUID owner() {
        return createStaffMember(email(), "Sahip", "OWNER");
    }

    private String ownerToken() {
        return staffAccessToken(owner());
    }

    private List<String> rolesOf(UUID staffId) {
        return jdbcTemplate.queryForList("""
                select r.code from staff_member_roles m
                join staff_roles r on r.id = m.staff_role_id
                where m.staff_member_id = ?
                order by r.code
                """, String.class, staffId);
    }

    @Test
    void anOwnerCreatesAStaffMemberThatMustChangeItsPasswordOnFirstSignIn() throws Exception {
        String address = email();

        mockMvc.perform(post("/admin/staff").header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", address,
                                "displayName", "Yeni Destek",
                                "password", "Ilk-Atanan-Parola-2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(address))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.roles").isEmpty());
    }

    @Test
    void theSameEmailCannotBeRegisteredTwice() throws Exception {
        String token = ownerToken();
        String address = email();
        String body = objectMapper.writeValueAsString(Map.of(
                "email", address, "displayName", "İlk", "password", "Ilk-Atanan-Parola-2026"));

        mockMvc.perform(post("/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mockMvc.perform(post("/admin/staff").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void theListAndTheDetailBothCarryTheRoleMemberships() throws Exception {
        String token = ownerToken();
        UUID target = createStaffMember(email(), "Destekçi", "SUPPORT_AGENT", "AUDITOR");

        mockMvc.perform(get("/admin/staff/" + target).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("AUDITOR", "SUPPORT_AGENT")));

        mockMvc.perform(get("/admin/staff").param("query", "Destekçi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].roles", containsInAnyOrder("AUDITOR", "SUPPORT_AGENT")));
    }

    @Test
    void theStaffListIsServedWhenNoSearchTermIsGiven() throws Exception {
        String token = ownerToken();
        createStaffMember(email(), "Arama Yapılmayan", "SUPPORT_AGENT");

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void grantingAndRevokingARoleIsWrittenToTheAuditTrail() throws Exception {
        String token = ownerToken();
        UUID target = createStaffMember(email(), "Rolsüz");

        mockMvc.perform(post("/admin/staff/" + target + "/roles/SUPPORT_AGENT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(List.of("SUPPORT_AGENT"), rolesOf(target));

        mockMvc.perform(delete("/admin/staff/" + target + "/roles/SUPPORT_AGENT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(List.of(), rolesOf(target));

        Map<String, Object> granted = jdbcTemplate.queryForMap("""
                select e.resource_id, d.before_value, d.after_value
                from admin_audit_entries e join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = 'staff.member.role.grant'
                """);
        assertEquals(target.toString(), granted.get("resource_id"));
        assertTrue(((String) granted.get("after_value")).contains("SUPPORT_AGENT"));

        Map<String, Object> revoked = jdbcTemplate.queryForMap("""
                select d.before_value, d.after_value
                from admin_audit_entries e join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = 'staff.member.role.revoke'
                """);
        assertTrue(((String) revoked.get("before_value")).contains("SUPPORT_AGENT"));
        assertTrue(((String) revoked.get("after_value")).contains("roles=[]"));
    }

    @Test
    void grantingARoleTwiceOpensNoSecondRow() throws Exception {
        String token = ownerToken();
        UUID target = createStaffMember(email(), "Rolsüz");

        mockMvc.perform(post("/admin/staff/" + target + "/roles/AUDITOR")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/admin/staff/" + target + "/roles/AUDITOR")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        assertEquals(List.of("AUDITOR"), rolesOf(target));
    }

    @Test
    void anUnknownRoleCodeIsNotFound() throws Exception {
        UUID target = createStaffMember(email(), "Rolsüz");

        mockMvc.perform(post("/admin/staff/" + target + "/roles/HAYALET_ROL")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anActorCannotGrantARoleWhosePermissionsTheyDoNotHold() throws Exception {
        String supportToken = staffAccessToken(createStaffMember(email(), "Destek", "SUPPORT_AGENT"));
        UUID target = createStaffMember(email(), "Hedef");

        mockMvc.perform(post("/admin/staff/" + target + "/roles/FINANCE_OFFICER")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isForbidden());

        assertEquals(List.of(), rolesOf(target));
    }

    @Test
    void deactivatingAStaffMemberKillsTheirOpenTokens() throws Exception {
        String ownerToken = ownerToken();
        UUID target = createStaffMember(email(), "Kapatılacak", "CATALOG_EDITOR");
        String targetToken = staffAccessToken(target);

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/admin/staff/" + target).header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOwnerCannotResetTheirOwnPasswordThroughTheAdministrationEndpoint() throws Exception {
        UUID actorId = owner();
        String token = staffAccessToken(actorId);
        int version = jdbcTemplate.queryForObject(
                "select token_version from staff_members where id = ?", Integer.class, actorId);

        mockMvc.perform(post("/admin/staff/" + actorId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Kendime-Atayamam-2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_MEMBER_PASSWORD_RESET_SELF"));

        assertEquals(version, jdbcTemplate.queryForObject(
                "select token_version from staff_members where id = ?", Integer.class, actorId));
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "select must_change_password from staff_members where id = ?", Boolean.class, actorId));
    }

    @Test
    void anOwnerUnlocksAnotherLockedStaffMemberAndAssignsAOneTimePassword() throws Exception {
        String ownerToken = ownerToken();
        String address = email();
        UUID target = createStaffMember(address, "Kilitli Destek", "CATALOG_EDITOR");
        String oldToken = staffAccessToken(target);

        for (int n = 0; n < 5; n++) {
            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", address, "password", "yanlis-" + n))))
                    .andExpect(status().isUnauthorized());
        }
        assertNotNull(jdbcTemplate.queryForObject(
                "select locked_until from staff_members where id = ?", java.sql.Timestamp.class, target));

        mockMvc.perform(post("/admin/staff/" + target + "/password")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Yonetici-Kurtardi-2026"))))
                .andExpect(status().isOk());

        Map<String, Object> recovered = jdbcTemplate.queryForMap("""
                select must_change_password, failed_login_attempts, locked_until, token_version
                from staff_members where id = ?
                """, target);
        assertEquals(Boolean.TRUE, recovered.get("must_change_password"));
        assertEquals(0, ((Number) recovered.get("failed_login_attempts")).intValue());
        assertEquals(null, recovered.get("locked_until"));
        assertEquals(1, ((Number) recovered.get("token_version")).intValue());

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", address, "password", "Yonetici-Kurtardi-2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void resettingAPasswordIsWrittenToTheAuditTrail() throws Exception {
        String ownerToken = ownerToken();
        UUID target = createStaffMember(email(), "Denetlenen", "CATALOG_EDITOR");
        jdbcTemplate.update("""
                update staff_members
                   set failed_login_attempts = 4,
                       locked_until = now() + interval '10 minutes'
                 where id = ?
                """, target);

        mockMvc.perform(post("/admin/staff/" + target + "/password")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Denetim-Atadi-2026"))))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select e.resource_id, e.action, d.before_value, d.after_value
                from admin_audit_entries e join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = 'staff.member.password.reset'
                """);
        assertEquals(target.toString(), row.get("resource_id"));
        String before = (String) row.get("before_value");
        String after = (String) row.get("after_value");
        assertTrue(before.contains("mustChangePassword=false"));
        assertTrue(before.contains("failedLoginAttempts=4"));
        assertTrue(before.contains("lockedUntil="));
        assertTrue(before.contains("tokenVersion=0"));
        assertTrue(after.contains("mustChangePassword=true"));
        assertTrue(after.contains("failedLoginAttempts=0"));
        assertTrue(after.contains("lockedUntil=null"));
        assertTrue(after.contains("tokenVersion=1"));
        assertTrue(!before.contains("$2a$") && !after.contains("$2a$"),
                "Denetim satırı parola özetini taşıyamaz");
    }

    @Test
    void anAdministratorResetsAPasswordAndTheAccountIsBackToFirstSignIn() throws Exception {
        String ownerToken = ownerToken();
        String address = email();
        UUID target = createStaffMember(address, "Parolası Sıfırlanan", "CATALOG_EDITOR");
        String oldToken = staffAccessToken(target);

        mockMvc.perform(post("/admin/staff/" + target + "/password")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Yonetici-Atadi-2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", address, "password", "Yonetici-Atadi-2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void staffWithoutTheManagePermissionAreRefusedByEveryWritingEndpoint() throws Exception {
        String auditorToken = staffAccessToken(createStaffMember(email(), "Denetçi", "AUDITOR"));
        UUID target = createStaffMember(email(), "Hedef", "SUPPORT_AGENT");

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/staff/" + target).header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/staff").header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email(), "password", "Bir-Parola-Daha-2026"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/staff/" + target).header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/staff/" + target + "/roles/AUDITOR")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/staff/" + target + "/roles/SUPPORT_AGENT")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/staff/" + target + "/password")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Bir-Parola-Daha-2026"))))
                .andExpect(status().isForbidden());

        assertEquals(List.of("SUPPORT_AGENT"), rolesOf(target));
    }

    @Test
    void aRejectedStaffRequestStillLeavesAnAuditRow() throws Exception {
        String auditorToken = staffAccessToken(createStaffMember(email(), "Denetçi", "AUDITOR"));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        assertEquals(1, (long) jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entries where permission = 'staff.read' "
                        + "and allowed = false", Long.class));
    }

    @Test
    void anUnknownSortFieldIsARequestErrorNotAServerError() throws Exception {
        mockMvc.perform(get("/admin/staff").param("sort", "passwordHash,desc")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theListNeverCarriesThePasswordHash() throws Exception {
        createStaffMember(email(), "Gizli", "SUPPORT_AGENT");

        String body = mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
        }).toString().contains("content"));
        assertTrue(!body.contains("passwordHash") && !body.contains("$2a$"),
                "Personel listesi parola özetini taşıyamaz: " + body);
    }
}
