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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAssignmentReadIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'assignment-read-%@iskoala.com'");
    }

    private String tokenFor(String... roles) {
        String subject = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into staff_members (id, keycloak_subject, email, display_name, is_active, created_at)
                values (?, ?, ?, ?, true, now())
                """, UUID.randomUUID(), subject, "assignment-read-" + subject + "@iskoala.com", "Okuyucu");

        return StaffTokenIssuer.tokenFor(subject, List.of(roles));
    }

    @Test
    void aFinanceAnalystReachesTheAssignmentReadEndpoint() throws Exception {
        mockMvc.perform(get("/admin/job-assignments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDisputeHandlerCannotReadTheEscrowBehindAnAssignment() throws Exception {
        mockMvc.perform(get("/admin/job-assignments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("DISPUTE_HANDLER")))
                .andExpect(status().isForbidden());
    }
}
