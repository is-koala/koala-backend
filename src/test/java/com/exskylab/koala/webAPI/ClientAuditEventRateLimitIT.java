package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.identity-reveal.max-attempts=2",
        "rate-limit.identity-reveal.window=1h",
        "rate-limit.audit-event.max-attempts=2",
        "rate-limit.audit-event.window=1h"
})
class ClientAuditEventRateLimitIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL_PREFIX = "client-audit-limit-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearTheTrail() {
        jdbc.update("delete from admin_audit_entries");
        deleteStaffMembersLike(STAFF_EMAIL_PREFIX + "%");
    }

    @Test
    void aStaffMemberIsCutOffAfterTheHourlyBudgetAndTheRefusalLeavesNoRowEvenWithAQueryParameter() throws Exception {
        String analyst = staffTokenFor(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com", "Finans Analisti",
                "FINANCE_ANALYST");

        export(analyst).andExpect(status().isCreated());
        export(analyst).andExpect(status().isCreated());
        export(analyst)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AUDIT_EVENT_TOO_MANY"));

        List<Map<String, Object>> rows = jdbc.queryForList("select action, allowed from admin_audit_entries");
        assertEquals(2, rows.size(), rows.toString());
        assertTrue(rows.stream().allMatch(row -> "export".equals(row.get("action"))
                && Boolean.TRUE.equals(row.get("allowed"))), rows.toString());

        String other = staffTokenFor(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com", "Başka Analist",
                "FINANCE_ANALYST");
        export(other).andExpect(status().isCreated());
    }

    private ResultActions export(String token) throws Exception {
        return mockMvc.perform(post("/admin/audit-log/events").param("x", "1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"EXPORT\",\"resourceType\":\"JOB_ASSIGNMENTS\",\"rowCount\":1,"
                        + "\"piiIncluded\":false}"));
    }
}
