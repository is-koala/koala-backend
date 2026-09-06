package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.StaffTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffIdentityIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from staff_members where email like 'identity-%@iskoala.com'");
    }

    private String staffMember(String displayName) {
        String subject = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into staff_members (id, keycloak_subject, email, display_name, is_active, created_at)
                values (?, ?, ?, ?, true, now())
                """, UUID.randomUUID(), subject, "identity-" + subject + "@iskoala.com", displayName);
        return subject;
    }

    @Test
    void theIdentityCarriesTheStaffRecordItsRolesAndItsResolvedPermissions() throws Exception {
        String subject = staffMember("Trust Lideri");
        String token = StaffTokenIssuer.tokenFor(subject, List.of("TRUST_LEAD"));

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffId").exists())
                .andExpect(jsonPath("$.data.displayName").value("Trust Lideri"))
                .andExpect(jsonPath("$.data.email").value("identity-" + subject + "@iskoala.com"))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder(
                        "DISPUTE_HANDLER", "MODERATOR", "SUPPORT_AGENT", "TRUST_LEAD")))
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder(
                        "content.moderate", "dispute.resolve", "user.read", "user.read_pii")));
    }

    @Test
    void aValidTokenWithoutAStaffRecordIsRefusedWithAccessDenied() throws Exception {
        String token = StaffTokenIssuer.tokenFor(UUID.randomUUID().toString(), List.of("OWNER"));

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void aStaffMemberWithNoRoleStillLearnsWhoTheyAre() throws Exception {
        String subject = staffMember("Rolsüz Personel");
        String token = StaffTokenIssuer.tokenFor(subject, List.of());

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void theMarketplaceBridgeHasNoStaffIdentityToReturn() throws Exception {
        mockMvc.perform(get("/admin/me").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void anAnonymousRequestNeverLearnsAnything() throws Exception {
        mockMvc.perform(get("/admin/me"))
                .andExpect(status().isUnauthorized());
    }
}
