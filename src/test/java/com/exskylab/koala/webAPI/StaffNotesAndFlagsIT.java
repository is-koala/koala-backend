package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffNotesAndFlagsIT extends AbstractStaffIntegrationTest {

    private static final String EMAIL_PATTERN = "notes-%@iskoala.com";
    private static final String NOTE_BODY = "Müşteri telefonda kaba konuştu, ikinci uyarı verildi.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID subjectId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries where actor_staff_id in "
                + "(select id from staff_members where email like ?)", EMAIL_PATTERN);
        deleteStaffMembersLike(EMAIL_PATTERN);
    }

    private UUID staff(String displayName, String... roles) {
        return createStaffMember("notes-" + UUID.randomUUID() + "@iskoala.com", displayName, roles);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, UUID staffId) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + staffAccessToken(staffId)));
    }

    private ResultActions createNote(UUID staffId, UUID resourceId, String body) throws Exception {
        return perform(post("/admin/notes")
                .param("resourceType", "USER")
                .param("resourceId", resourceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":" + json(body) + "}"), staffId);
    }

    private UUID createdNoteId(UUID staffId, UUID resourceId, String body) throws Exception {
        String response = createNote(staffId, resourceId, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(response.replaceAll(".*\"data\":\\{\"id\":\"([^\"]+)\".*", "$1"));
    }

    private ResultActions setFlag(UUID staffId, UUID resourceId, String kind, String label) throws Exception {
        return perform(put("/admin/flags")
                .param("resourceType", "USER")
                .param("resourceId", resourceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"" + kind + "\",\"label\":" + json(label) + "}"), staffId);
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String normalise(String body, UUID id) {
        return body.replace(id.toString(), "{id}")
                .replaceAll("\"timeStamp\":\"[^\"]*\"", "\"timeStamp\":\"-\"")
                .replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"-\"");
    }

    private List<Map<String, Object>> auditRowsOf(UUID staffId) {
        return jdbcTemplate.queryForList("""
                select e.action, e.permission, e.resource_type, e.resource_id, e.allowed, e.mutating,
                       d.request_body, d.before_value, d.after_value
                from admin_audit_entries e
                left join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.actor_staff_id = ?
                order by e.occurred_at
                """, staffId);
    }

    @Test
    void aSupportAgentWritesANoteAndReadsItBackNewestFirstWithItsAuthor() throws Exception {
        UUID author = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID colleague = staff("Destek Mehmet", "SUPPORT_AGENT");

        createNote(author, subjectId, NOTE_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resourceType").value("USER"))
                .andExpect(jsonPath("$.data.resourceId").value(subjectId.toString()))
                .andExpect(jsonPath("$.data.authorStaffId").value(author.toString()))
                .andExpect(jsonPath("$.data.authorDisplayName").value("Destek Ayşe"))
                .andExpect(jsonPath("$.data.body").value(NOTE_BODY))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.editedAt", nullValue()));
        createNote(colleague, subjectId, "İkinci not").andExpect(status().isCreated());
        createNote(colleague, UUID.randomUUID(), "Başka kullanıcının notu").andExpect(status().isCreated());

        perform(get("/admin/notes").param("resourceType", "USER").param("resourceId", subjectId.toString()),
                colleague)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].body").value("İkinci not"))
                .andExpect(jsonPath("$.data.content[0].authorDisplayName").value("Destek Mehmet"))
                .andExpect(jsonPath("$.data.content[1].body").value(NOTE_BODY))
                .andExpect(jsonPath("$.data.content[1].authorStaffId").value(author.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        perform(get("/admin/notes").param("resourceType", "USER").param("resourceId", subjectId.toString())
                .param("sort", "body,asc"), colleague)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"));
    }

    @Test
    void theBodyIsValidatedOnTheDto() throws Exception {
        UUID author = staff("Destek Ayşe", "SUPPORT_AGENT");

        createNote(author, subjectId, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("body"))
                .andExpect(jsonPath("$.details[0].message").value("Not metni boş olamaz."));
        createNote(author, subjectId, "x".repeat(2001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("body"))
                .andExpect(jsonPath("$.details[0].message").value("Not metni en fazla 2000 karakter olabilir."));
        createNote(author, subjectId, "x".repeat(2000)).andExpect(status().isCreated());
    }

    @Test
    void onlyTheAuthorOrStaffManageEditsANoteAndOthersCannotTellItExists() throws Exception {
        UUID author = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID colleague = staff("Destek Mehmet", "SUPPORT_AGENT");
        UUID owner = staff("Sahip", "OWNER");
        UUID noteId = createdNoteId(author, subjectId, NOTE_BODY);

        String real = normalise(perform(patch("/admin/notes/" + noteId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Değişti\"}"), colleague)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOTE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString(), noteId);
        UUID random = UUID.randomUUID();
        String missing = normalise(perform(patch("/admin/notes/" + random)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Değişti\"}"), colleague)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(), random);
        assertEquals(real, missing, "yazar olmayan personel başkasının notunun varlığını yanıttan çıkarabiliyor");

        perform(patch("/admin/notes/" + noteId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yazar düzeltti\"}"), author)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Yazar düzeltti"))
                .andExpect(jsonPath("$.data.editedAt").exists())
                .andExpect(jsonPath("$.data.authorStaffId").value(author.toString()));

        perform(patch("/admin/notes/" + noteId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Sahip düzeltti\"}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Sahip düzeltti"))
                .andExpect(jsonPath("$.data.authorStaffId").value(author.toString()));
    }

    @Test
    void onlyTheAuthorOrStaffManageDeletesANote() throws Exception {
        UUID author = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID colleague = staff("Destek Mehmet", "SUPPORT_AGENT");
        UUID owner = staff("Sahip", "OWNER");
        UUID mine = createdNoteId(author, subjectId, NOTE_BODY);
        UUID theirs = createdNoteId(colleague, subjectId, "Meslektaşın notu");

        String real = normalise(perform(delete("/admin/notes/" + theirs), author)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(), theirs);
        UUID random = UUID.randomUUID();
        String missing = normalise(perform(delete("/admin/notes/" + random), author)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(), random);
        assertEquals(real, missing, "yazar olmayan personel başkasının notunun varlığını yanıttan çıkarabiliyor");

        perform(delete("/admin/notes/" + mine), author).andExpect(status().isOk());
        perform(delete("/admin/notes/" + theirs), owner).andExpect(status().isOk());

        perform(get("/admin/notes").param("resourceType", "USER").param("resourceId", subjectId.toString()),
                author)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from staff_notes where resource_id = ?", Integer.class, subjectId));
    }

    @Test
    void aTrustLeadSetsUpdatesAndClearsAFlagWithoutDuplicatingIt() throws Exception {
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        UUID otherLead = staff("İkinci Lider", "TRUST_LEAD");

        setFlag(lead, subjectId, "RISK", "Sahte belge şüphesi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RISK"))
                .andExpect(jsonPath("$.data.label").value("Sahte belge şüphesi"))
                .andExpect(jsonPath("$.data.setByStaffId").value(lead.toString()))
                .andExpect(jsonPath("$.data.setByDisplayName").value("Güven Lideri"))
                .andExpect(jsonPath("$.data.clearedAt", nullValue()));
        setFlag(otherLead, subjectId, "RISK", "Belge doğrulandı, izlemede")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("Belge doğrulandı, izlemede"))
                .andExpect(jsonPath("$.data.setByStaffId").value(otherLead.toString()));
        setFlag(lead, subjectId, "VIP", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label", nullValue()));

        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from staff_flags where resource_id = ?", Integer.class, subjectId));

        perform(get("/admin/flags").param("resourceType", "USER").param("resourceId", subjectId.toString()),
                lead)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].kind", containsInAnyOrder("RISK", "VIP")));

        perform(delete("/admin/flags").param("resourceType", "USER").param("resourceId", subjectId.toString())
                .param("kind", "RISK"), lead)
                .andExpect(status().isOk());
        perform(delete("/admin/flags").param("resourceType", "USER").param("resourceId", subjectId.toString())
                .param("kind", "RISK"), lead)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STAFF_FLAG_NOT_FOUND"));

        perform(get("/admin/flags").param("resourceType", "USER").param("resourceId", subjectId.toString()),
                lead)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].kind").value("VIP"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from staff_flags where resource_id = ? and cleared_at is not null",
                Integer.class, subjectId));

        setFlag(lead, subjectId, "RISK", "Yeniden").andExpect(status().isOk());
        assertEquals(3, jdbcTemplate.queryForObject(
                "select count(*) from staff_flags where resource_id = ?", Integer.class, subjectId));
    }

    @Test
    void theBadgeReadReturnsFlagsForManyResourcesAndCapsTheBatchAtTwoHundred() throws Exception {
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        UUID flagged = UUID.randomUUID();
        UUID alsoFlagged = UUID.randomUUID();
        UUID clean = UUID.randomUUID();
        setFlag(lead, flagged, "RISK", null).andExpect(status().isOk());
        setFlag(lead, flagged, "WATCH", null).andExpect(status().isOk());
        setFlag(lead, alsoFlagged, "VIP", null).andExpect(status().isOk());
        setFlag(lead, clean, "OTHER", "Kapandı").andExpect(status().isOk());
        perform(delete("/admin/flags").param("resourceType", "USER").param("resourceId", clean.toString())
                .param("kind", "OTHER"), lead).andExpect(status().isOk());

        perform(get("/admin/flags").param("resourceType", "USER")
                .param("resourceIds", flagged + "," + alsoFlagged + "," + clean), lead)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].resourceId", containsInAnyOrder(
                        flagged.toString(), flagged.toString(), alsoFlagged.toString())));

        perform(get("/admin/flags").param("resourceType", "COMPANY").param("resourceIds", flagged.toString()),
                lead)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        String twoHundred = IntStream.range(0, 200).mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        perform(get("/admin/flags").param("resourceType", "USER").param("resourceIds", twoHundred), lead)
                .andExpect(status().isOk());
        perform(get("/admin/flags").param("resourceType", "USER")
                .param("resourceIds", twoHundred + "," + UUID.randomUUID()), lead)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAFF_FLAG_BATCH_TOO_LARGE"));
        perform(get("/admin/flags").param("resourceType", "USER"), lead)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAFF_FLAG_RESOURCE_REQUIRED"));
    }

    @Test
    void staffWithoutThePermissionIsRefusedIdenticallyWhetherOrNotAnythingExists() throws Exception {
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        UUID agent = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID analyst = staff("Finans", "FINANCE_ANALYST");
        UUID noteId = createdNoteId(agent, subjectId, NOTE_BODY);
        setFlag(lead, subjectId, "RISK", null).andExpect(status().isOk());

        assertRefusedIdentically(agent, id -> get("/admin/flags").param("resourceType", "USER")
                .param("resourceId", id.toString()), subjectId);
        assertRefusedIdentically(agent, id -> put("/admin/flags").param("resourceType", "USER")
                .param("resourceId", id.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"VIP\"}"), subjectId);
        assertRefusedIdentically(analyst, id -> get("/admin/notes").param("resourceType", "USER")
                .param("resourceId", id.toString()), subjectId);
        assertRefusedIdentically(analyst, id -> patch("/admin/notes/" + id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"), noteId);
        assertRefusedIdentically(analyst, id -> delete("/admin/notes/" + id), noteId);
    }

    private void assertRefusedIdentically(UUID staffId, java.util.function.Function<UUID,
            MockHttpServletRequestBuilder> request, UUID existing) throws Exception {
        String real = normalise(perform(request.apply(existing), staffId)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), existing);
        UUID random = UUID.randomUUID();
        String missing = normalise(perform(request.apply(random), staffId)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), random);
        assertEquals(real, missing, "izinsiz personel var olan ile olmayan kaydı yanıttan ayırt edebiliyor");
    }

    @Test
    void theNoteListAndTheBadgeReadAreBuiltWithoutAQueryPerRow() throws Exception {
        UUID agent = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID colleague = staff("Destek Mehmet", "SUPPORT_AGENT");
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        createdNoteId(agent, subjectId, "Bir");
        createdNoteId(colleague, subjectId, "İki");
        createdNoteId(agent, subjectId, "Üç");
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        setFlag(lead, subjectId, "RISK", null).andExpect(status().isOk());
        setFlag(lead, second, "VIP", null).andExpect(status().isOk());
        setFlag(lead, third, "WATCH", null).andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            perform(get("/admin/notes").param("resourceType", "USER").param("resourceId", subjectId.toString())
                    .param("size", "1"), agent).andExpect(status().isOk());
            long statementsForOneNote = statistics.getPrepareStatementCount();
            long fetchesForOneNote = statistics.getEntityFetchCount();

            statistics.clear();
            perform(get("/admin/notes").param("resourceType", "USER").param("resourceId", subjectId.toString())
                    .param("size", "3"), agent)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(3)));
            assertEquals(statementsForOneNote, statistics.getPrepareStatementCount(),
                    "not listesi satır sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, fetchesForOneNote, "not listesi satır başına lazy entity yüklüyor");
            assertEquals(0, statistics.getEntityFetchCount(), "not listesi satır başına lazy entity yüklüyor");

            statistics.clear();
            perform(get("/admin/flags").param("resourceType", "USER").param("resourceIds", subjectId.toString()),
                    lead).andExpect(status().isOk());
            long statementsForOneResource = statistics.getPrepareStatementCount();

            statistics.clear();
            perform(get("/admin/flags").param("resourceType", "USER")
                    .param("resourceIds", subjectId + "," + second + "," + third), lead)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)));
            assertEquals(statementsForOneResource, statistics.getPrepareStatementCount(),
                    "rozet okuması kaynak sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, statistics.getEntityFetchCount(), "rozet okuması satır başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void writingAndDeletingNotesAndFlagsLeavesAnAuditRowWithBeforeAndAfterValues() throws Exception {
        UUID agent = staff("Destek Ayşe", "SUPPORT_AGENT");
        UUID lead = staff("Güven Lideri", "TRUST_LEAD");
        UUID noteId = createdNoteId(agent, subjectId, NOTE_BODY);
        perform(patch("/admin/notes/" + noteId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Düzeltildi\"}"), agent)
                .andExpect(status().isOk());
        perform(delete("/admin/notes/" + noteId), agent).andExpect(status().isOk());
        setFlag(lead, subjectId, "RISK", "Şüpheli").andExpect(status().isOk());
        perform(delete("/admin/flags").param("resourceType", "USER").param("resourceId", subjectId.toString())
                .param("kind", "RISK"), lead).andExpect(status().isOk());

        List<Map<String, Object>> noteRows = auditRowsOf(agent);
        assertEquals(List.of("staff.note.create", "staff.note.edit", "staff.note.delete"),
                noteRows.stream().map(row -> row.get("action")).toList());
        assertTrue(noteRows.stream().allMatch(row -> "note.write".equals(row.get("permission"))
                && Boolean.TRUE.equals(row.get("allowed")) && Boolean.TRUE.equals(row.get("mutating"))
                && "notes".equals(row.get("resource_type"))));
        assertTrue(String.valueOf(noteRows.get(0).get("after_value")).contains(NOTE_BODY));
        assertTrue(String.valueOf(noteRows.get(0).get("after_value")).contains("USER/" + subjectId));
        assertEquals(noteId.toString(), noteRows.get(1).get("resource_id"));
        assertTrue(String.valueOf(noteRows.get(1).get("before_value")).contains(NOTE_BODY));
        assertTrue(String.valueOf(noteRows.get(1).get("after_value")).contains("Düzeltildi"));
        assertTrue(String.valueOf(noteRows.get(2).get("before_value")).contains("Düzeltildi"));
        assertNull(noteRows.get(2).get("after_value"));

        List<Map<String, Object>> flagRows = auditRowsOf(lead);
        assertEquals(List.of("staff.flag.set", "staff.flag.clear"),
                flagRows.stream().map(row -> row.get("action")).toList());
        assertTrue(flagRows.stream().allMatch(row -> "flag.manage".equals(row.get("permission"))
                && "flags".equals(row.get("resource_type"))));
        assertTrue(String.valueOf(flagRows.get(0).get("after_value")).contains("kind=RISK label=Şüpheli"));
        assertTrue(String.valueOf(flagRows.get(1).get("before_value")).contains("cleared=false"));
        assertTrue(String.valueOf(flagRows.get(1).get("after_value")).contains("cleared=true"));
    }
}
