package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminObjectReadIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'object-read-%@iskoala.com'");
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("object-read-" + UUID.randomUUID() + "@iskoala.com", "Okuyucu", roles);
    }

    @Test
    void aCompanyApproverReachesAMissingCompany() throws Exception {
        mockMvc.perform(get("/admin/companies/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("COMPANY_APPROVER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aModeratorCannotReadACompany() throws Exception {
        mockMvc.perform(get("/admin/companies/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSupportAgentReachesAMissingUser() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("SUPPORT_AGENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCatalogEditorCannotReadAUser() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("CATALOG_EDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDisputeHandlerReachesAMissingDispute() throws Exception {
        mockMvc.perform(get("/admin/disputes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("DISPUTE_HANDLER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFinanceAnalystCannotReadADispute() throws Exception {
        mockMvc.perform(get("/admin/disputes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFinanceOfficerListsPayouts() throws Exception {
        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_OFFICER")))
                .andExpect(status().isOk());
    }

    @Test
    void aFinanceAnalystCannotListPayouts() throws Exception {
        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTrustLeadReachesAMissingIdentityReview() throws Exception {
        mockMvc.perform(get("/admin/identity-verifications/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("TRUST_LEAD")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCompanyApproverCannotReadAnIdentityReview() throws Exception {
        mockMvc.perform(get("/admin/identity-verifications/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("COMPANY_APPROVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTrustLeadReachesAMissingAccountDeletion() throws Exception {
        mockMvc.perform(get("/admin/account-deletions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("TRUST_LEAD")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSupportAgentCannotReadAnAccountDeletion() throws Exception {
        mockMvc.perform(get("/admin/account-deletions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("SUPPORT_AGENT")))
                .andExpect(status().isForbidden());
    }
}
