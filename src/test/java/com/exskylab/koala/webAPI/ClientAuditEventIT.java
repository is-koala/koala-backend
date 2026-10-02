package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffExportList;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientAuditEventIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL_PREFIX = "client-audit-";
    private static final String EVENTS = "/admin/audit-log/events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void clearTheTrail() {
        jdbc.update("delete from admin_audit_entries");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from admin_audit_entries");
        deleteStaffMembersLike(STAFF_EMAIL_PREFIX + "%");
    }

    @Test
    void anAssignmentExportIsRecordedUnderThePermissionOfTheListAndListedAsAnExport() throws Exception {
        UUID analyst = staff("Finans Analisti", "FINANCE_ANALYST");

        MvcResult result = record(analyst, """
                {"action":"EXPORT","resourceType":"JOB_ASSIGNMENTS",
                 "filters":"durum=CONFIRMED&from=2026-09-01T00:00:00Z","rowCount":437,"piiIncluded":false}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.action").value("export"))
                .andExpect(jsonPath("$.data.resourceType").value("job-assignments"))
                .andExpect(jsonPath("$.data.occurredAt").isNotEmpty())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        Map<String, Object> row = onlyExportRow();
        assertEquals(id, row.get("id").toString());
        assertEquals(analyst, row.get("actor_staff_id"));
        assertEquals("Finans Analisti", row.get("actor_display_name"));
        assertEquals("payment.read", row.get("permission"));
        assertEquals("job-assignments", row.get("resource_type"));
        assertEquals("", row.get("resource_id"));
        assertEquals(Boolean.TRUE, row.get("allowed"));
        assertEquals(Boolean.FALSE, row.get("mutating"));
        assertEquals("FINANCIAL", row.get("sensitivity"));
        String body = (String) row.get("request_body");
        assertTrue(body.contains("\"rowCount\":437") && body.contains("durum=CONFIRMED")
                && body.contains("\"piiIncluded\":false"), body);

        mockMvc.perform(get("/admin/audit-log").param("action", "export")
                        .header("Authorization", "Bearer " + staffAccessToken(staff("Sahip", "OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + id + "')].permission").value(hasItem("payment.read")));
    }

    @Test
    void theActorComesFromTheTokenAndActorFieldsInTheBodyAreIgnored() throws Exception {
        UUID officer = staff("Finans Sorumlusu", "FINANCE_OFFICER");
        UUID someoneElse = UUID.randomUUID();

        record(officer, """
                {"action":"EXPORT","resourceType":"PAYOUTS","rowCount":3,"piiIncluded":false,
                 "actorStaffId":"%s","actorDisplayName":"Sahte Personel","allowed":false,"permission":"none"}
                """.formatted(someoneElse))
                .andExpect(status().isCreated());

        Map<String, Object> row = onlyExportRow();
        assertEquals(officer, row.get("actor_staff_id"));
        assertEquals("Finans Sorumlusu", row.get("actor_display_name"));
        assertEquals("payout.reconcile", row.get("permission"));
        assertEquals(Boolean.TRUE, row.get("allowed"));
    }

    @Test
    void aStaffMemberWithoutTheListPermissionIsRefusedAndTheRefusalIsRecorded() throws Exception {
        UUID agent = staff("Destek", "SUPPORT_AGENT");

        record(agent, """
                {"action":"EXPORT","resourceType":"PAYOUTS","rowCount":10,"piiIncluded":false}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUDIT_EVENT_LIST_PERMISSION_MISSING"));

        Map<String, Object> row = onlyExportRow();
        assertEquals(agent, row.get("actor_staff_id"));
        assertEquals(Boolean.FALSE, row.get("allowed"));
        assertEquals("payout.reconcile", row.get("permission"));
        assertEquals("payouts", row.get("resource_type"));
    }

    @Test
    void personalDataInTheFilterSummaryIsRefusedBeforeAnythingIsRecorded() throws Exception {
        UUID moderator = staff("Moderatör", "MODERATOR");

        record(moderator, """
                {"action":"EXPORT","resourceType":"REPORTS","filters":"arama=ali.veli@example.com",
                 "rowCount":2,"piiIncluded":false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("filtersFreeOfPersonalData"));
        record(moderator, """
                {"action":"EXPORT","resourceType":"REPORTS","filters":"telefon=05321234567",
                 "rowCount":2,"piiIncluded":false}
                """)
                .andExpect(status().isBadRequest());

        assertEquals(0, exportRows().size());
    }

    @Test
    void anUnknownActionOrListOrAnOutOfRangePayloadIsRefusedAndLeavesNoRow() throws Exception {
        UUID owner = staff("Sahip", "OWNER");

        record(owner, """
                {"action":"DELETE","resourceType":"REPORTS","rowCount":2,"piiIncluded":false}
                """).andExpect(status().isBadRequest());
        record(owner, """
                {"action":"EXPORT","resourceType":"USERS","rowCount":2,"piiIncluded":false}
                """).andExpect(status().isBadRequest());
        record(owner, """
                {"action":"EXPORT","resourceType":"REPORTS","rowCount":1001,"piiIncluded":false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("rowCount"));
        record(owner, """
                {"action":"EXPORT","resourceType":"REPORTS","rowCount":2}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("piiIncluded"));
        record(owner, """
                {"action":"EXPORT","resourceType":"REPORTS","rowCount":2,"piiIncluded":false,
                 "filters":"%s"}
                """.formatted("d".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("filters"));

        assertEquals(0, exportRows().size());
    }

    @Test
    void anExportScopedToOneUserAppearsOnThatUsersTimelineAndPersonalDataRaisesTheSensitivity() throws Exception {
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        UUID user = UUID.randomUUID();

        record(lead, """
                {"action":"EXPORT","resourceType":"DISPUTES","rowCount":4,"piiIncluded":true,
                 "subjectType":"USER","subjectId":"%s"}
                """.formatted(user))
                .andExpect(status().isCreated());

        Map<String, Object> row = onlyExportRow();
        assertEquals("USER", row.get("subject_type"));
        assertEquals(user, row.get("subject_id"));
        assertEquals("PERSONAL_DATA", row.get("sensitivity"));

        mockMvc.perform(get("/admin/audit-log").param("subjectType", "USER").param("subjectId", user.toString())
                        .header("Authorization", "Bearer " + staffAccessToken(staff("Sahip", "OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("export"))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("disputes"));
    }

    @Test
    void anAnonymousCallerIsRefusedAndLeavesNoRow() throws Exception {
        mockMvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"EXPORT\",\"resourceType\":\"REPORTS\",\"rowCount\":1,\"piiIncluded\":false}"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, exportRows().size());
    }

    @Test
    void everyExportableListNamesThePermissionThatGuardsItsStaffList() {
        List<String> drift = new ArrayList<>();
        for (StaffExportList list : StaffExportList.values()) {
            RequiresPermission guard = listGuardOf("/admin/" + list.getResourceType());
            if (guard == null || !Arrays.asList(guard.value()).equals(List.of(list.getPermission()))) {
                drift.add(list + " → " + list.getPermission().getCode() + " ama GET /admin/"
                        + list.getResourceType() + " " + (guard == null ? "yok" : Arrays.toString(guard.value())));
            }
        }
        assertTrue(drift.isEmpty(), "Dışa aktarma kaydının izni listenin kendi izniyle ayrışmış: " + drift);
    }

    private RequiresPermission listGuardOf(String pattern) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            boolean get = entry.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET);
            if (get && entry.getKey().getPathPatternsCondition().getPatternValues().contains(pattern)) {
                return AnnotatedElementUtils.findMergedAnnotation(entry.getValue().getMethod(),
                        RequiresPermission.class);
            }
        }
        return null;
    }

    private ResultActions record(UUID staffId, String body) throws Exception {
        return mockMvc.perform(post(EVENTS)
                .header("Authorization", "Bearer " + staffAccessToken(staffId))
                .header("User-Agent", "koala-admin/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID staff(String displayName, String role) {
        return createStaffMember(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com", displayName, role);
    }

    private List<Map<String, Object>> exportRows() {
        return jdbc.queryForList("""
                select e.*, d.request_body
                from admin_audit_entries e
                left join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = 'export'
                """);
    }

    private Map<String, Object> onlyExportRow() {
        List<Map<String, Object>> rows = exportRows();
        assertEquals(1, rows.size(), "Tek bir dışa aktarma satırı bekleniyordu: " + rows);
        return rows.getFirst();
    }
}
