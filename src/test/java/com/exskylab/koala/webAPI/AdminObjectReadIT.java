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
}
