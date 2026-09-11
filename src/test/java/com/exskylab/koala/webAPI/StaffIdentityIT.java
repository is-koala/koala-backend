package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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

    private String emailFor(String marker) {
        return "identity-" + marker + "@iskoala.com";
    }

    @Test
    void theIdentityCarriesTheStaffRecordItsRolesAndItsResolvedPermissions() throws Exception {
        String email = emailFor(UUID.randomUUID().toString());
        String token = staffTokenFor(email, "Trust Lideri", "TRUST_LEAD");

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffId").exists())
                .andExpect(jsonPath("$.data.displayName").value("Trust Lideri"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder(
                        "DISPUTE_HANDLER", "MODERATOR", "SUPPORT_AGENT", "TRUST_LEAD")))
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder(
                        "content.moderate", "dispute.resolve", "user.deletion.manage",
                        "user.read", "user.read_pii", "identity.review")));
    }

    @Test
    void aSignedTokenForAStaffRecordThatNoLongerExistsIsRefused() throws Exception {
        UUID staffId = createStaffMember(emailFor(UUID.randomUUID().toString()), "Silinen", "OWNER");
        String token = staffAccessToken(staffId);
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id = ?", staffId);
        jdbcTemplate.update("delete from staff_members where id = ?", staffId);

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aStaffMemberWithNoRoleStillLearnsWhoTheyAre() throws Exception {
        String token = staffTokenFor(emailFor(UUID.randomUUID().toString()), "Rolsüz Personel");

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
