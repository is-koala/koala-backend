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

    private final UUID workerId = UUID.randomUUID();
    private final UUID employerId = UUID.randomUUID();

    @BeforeEach
    void clearTheTrail() {
        jdbc.update("delete from admin_audit_entries");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from users where id in (?, ?)", workerId, employerId);
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
        assertEquals(437, row.get("row_count"));
        assertEquals(Boolean.FALSE, row.get("pii_included"));
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
    void anExportFilteredByAWorkerAndAnEmployerLandsOnBothTimelinesWithItsFiguresInTheCoreRow() throws Exception {
        UUID analyst = staff("Finans Analisti", "FINANCE_ANALYST");
        insertUser(workerId);
        insertUser(employerId);

        record(analyst, """
                {"action":"EXPORT","resourceType":"JOB_ASSIGNMENTS","rowCount":4,"piiIncluded":true,
                 "subjectType":"USER","subjectId":"%s","relatedSubjectType":"USER","relatedSubjectId":"%s"}
                """.formatted(workerId, employerId))
                .andExpect(status().isCreated());

        Map<String, Object> row = onlyExportRow();
        assertEquals("USER", row.get("subject_type"));
        assertEquals(workerId, row.get("subject_id"));
        assertEquals("USER", row.get("related_subject_type"));
        assertEquals(employerId, row.get("related_subject_id"));
        assertEquals(4, row.get("row_count"));
        assertEquals(Boolean.TRUE, row.get("pii_included"));
        assertEquals("FINANCIAL", row.get("sensitivity"));

        jdbc.update("delete from admin_audit_entry_details");
        String owner = staffAccessToken(staff("Sahip", "OWNER"));
        mockMvc.perform(get("/admin/audit-log").param("action", "export").param("subjectId", employerId.toString())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].resourceType").value("job-assignments"))
                .andExpect(jsonPath("$.data.content[0].relatedSubjectId").value(employerId.toString()))
                .andExpect(jsonPath("$.data.content[0].rowCount").value(4))
                .andExpect(jsonPath("$.data.content[0].piiIncluded").value(true))
                .andExpect(jsonPath("$.data.content[0].requestBody").doesNotExist());
    }

    @Test
    void aPersonalDataExportOfANormalListIsKeptAsPersonalData() throws Exception {
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");

        record(lead, """
                {"action":"EXPORT","resourceType":"DISPUTES","rowCount":4,"piiIncluded":true}
                """)
                .andExpect(status().isCreated());

        assertEquals("PERSONAL_DATA", onlyExportRow().get("sensitivity"));
    }

    @Test
    void aSubjectThatDoesNotExistIsRefusedButOneWhoDeletedTheirAccountIsAccepted() throws Exception {
        UUID analyst = staff("Finans Analisti", "FINANCE_ANALYST");
        insertUser(workerId);
        jdbc.update("update users set is_deleted = true where id = ?", workerId);

        record(analyst, """
                {"action":"EXPORT","resourceType":"JOB_ASSIGNMENTS","rowCount":1,"piiIncluded":false,
                 "subjectType":"USER","subjectId":"%s"}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUDIT_EVENT_SUBJECT_NOT_FOUND"));
        record(analyst, """
                {"action":"EXPORT","resourceType":"JOB_ASSIGNMENTS","rowCount":1,"piiIncluded":false,
                 "subjectType":"USER","subjectId":"%s","relatedSubjectType":"COMPANY","relatedSubjectId":"%s"}
                """.formatted(workerId, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUDIT_EVENT_SUBJECT_NOT_FOUND"));
        assertEquals(0, exportRows().size());

        record(analyst, """
                {"action":"EXPORT","resourceType":"JOB_ASSIGNMENTS","rowCount":1,"piiIncluded":false,
                 "subjectType":"USER","subjectId":"%s"}
                """.formatted(workerId))
                .andExpect(status().isCreated());
        assertEquals(workerId, onlyExportRow().get("subject_id"));
    }

    @Test
    void aQueryParameterNeverAddsASecondRowWhetherTheEventIsAcceptedOrRefused() throws Exception {
        UUID analyst = staff("Finans Analisti", "FINANCE_ANALYST");
        UUID agent = staff("Destek", "SUPPORT_AGENT");

        mockMvc.perform(post(EVENTS).param("x", "1")
                        .header("Authorization", "Bearer " + staffAccessToken(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"EXPORT\",\"resourceType\":\"PAYOUTS\",\"rowCount\":1,"
                                + "\"piiIncluded\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(EVENTS).param("x", "1")
                        .header("Authorization", "Bearer " + staffAccessToken(agent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"EXPORT\",\"resourceType\":\"PAYOUTS\",\"rowCount\":1,"
                                + "\"piiIncluded\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(EVENTS).param("x", "1")
                        .header("Authorization", "Bearer " + staffAccessToken(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"EXPORT\",\"resourceType\":\"JOB_ASSIGNMENTS\",\"rowCount\":1,"
                                + "\"piiIncluded\":false}"))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select action, allowed, resource_type from admin_audit_entries order by occurred_at");
        assertEquals(3, rows.size(), rows.toString());
        assertTrue(rows.stream().allMatch(row -> "export".equals(row.get("action"))), rows.toString());
        assertEquals(List.of(false, false, true), rows.stream().map(row -> row.get("allowed")).toList());
    }

    @Test
    void encodedPersonalDataInTheFilterSummaryIsRefusedToo() throws Exception {
        UUID moderator = staff("Moderatör", "MODERATOR");

        for (String filters : List.of("arama=ali%40example.com", "telefon=0532%20123%2045%2067",
                "telefon=0532+123+45+67")) {
            record(moderator, """
                    {"action":"EXPORT","resourceType":"REPORTS","filters":"%s","rowCount":2,"piiIncluded":false}
                    """.formatted(filters))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("filtersFreeOfPersonalData"));
        }

        assertEquals(0, exportRows().size());
    }

    @Test
    void anOversizedBodyIsRefusedBeforeItIsParsedAndALongSummaryOnlyForItsLength() throws Exception {
        UUID moderator = staff("Moderatör", "MODERATOR");

        record(moderator, """
                {"action":"EXPORT","resourceType":"REPORTS","filters":"%s","rowCount":2,"piiIncluded":false}
                """.formatted("a".repeat(200_000)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_TOO_LARGE"));

        long started = System.nanoTime();
        record(moderator, """
                {"action":"EXPORT","resourceType":"REPORTS","filters":"%s","rowCount":2,"piiIncluded":false}
                """.formatted("ali@example.com&" + "a".repeat(15_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("filters"));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMillis < 3_000, "15 bin karakterlik süzgeç özeti " + elapsedMillis + " ms sürdü");
        assertEquals(0, exportRows().size());
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

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified, first_name, last_name) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false, 'Konu', 'Kişi')",
                id, id + "@example.com");
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
