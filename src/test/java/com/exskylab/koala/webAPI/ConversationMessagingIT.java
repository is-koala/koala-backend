package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConversationMessagingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID employerId = UUID.randomUUID();
    private final UUID firstWorkerId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID ghostId = UUID.randomUUID();
    private final UUID employerPictureId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID directId = UUID.randomUUID();
    private final UUID crewId = UUID.randomUUID();
    private final List<UUID> users = List.of(employerId, firstWorkerId, secondWorkerId, outsiderId);

    @BeforeEach
    void setUp() {
        jdbc.update("insert into media (id, created_at, media_type, file_name, file_type, file_url, file_size) "
                + "values (?, now(), 'IMAGE', 'isveren.jpg', 'image/jpeg', 'profiles/isveren.jpg', 1024)",
                employerPictureId);
        insertUser(employerId, "İşveren", "Bey");
        jdbc.update("update users set profile_picture_id = ? where id = ?", employerPictureId, employerId);
        insertUser(firstWorkerId, "Ayşe", "Yılmaz");
        insertUser(secondWorkerId, "Mehmet", "Kaya");
        insertUser(outsiderId, "Yabancı", "Kişi");
        insertUser(contactId, "Yetkili", "Hanım");
        insertUser(ghostId, "Anonim", "Kullanıcı");
        jdbc.update("update users set is_deleted = true, deleted_at = now() where id = ?", ghostId);
        for (UUID userId : users) {
            jdbc.update("insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, "
                            + "push_token) values (?, now(), false, ?, 'ANDROID', 'telefon', ?, ?)",
                    UUID.randomUUID(), userId, UUID.randomUUID().toString(), "fcm-" + userId);
        }
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Sohbet", "CHAT_PUSH_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, "
                        + "headcount, start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 2, now() + interval '1 day', "
                        + "now() + interval '2 days', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, employerId, categoryId, "Sohbet testi " + jobId);
        for (UUID workerId : List.of(firstWorkerId, secondWorkerId)) {
            jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                            + "payment_status, work_hours, total_charged) "
                            + "values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0, 110.00)",
                    UUID.randomUUID(), jobId, workerId);
        }
        jdbc.update("insert into conversations (id, created_at, is_deleted, job_id, employer_id, worker_id, kind) "
                + "values (?, now(), false, ?, ?, ?, 'DIRECT')", directId, jobId, employerId, firstWorkerId);
        addParticipant(directId, employerId);
        addParticipant(directId, firstWorkerId);
        jdbc.update("insert into conversations (id, created_at, is_deleted, job_id, employer_id, kind) "
                + "values (?, now(), false, ?, ?, 'GROUP')", crewId, jobId, employerId);
        addParticipant(crewId, employerId);
        addParticipant(crewId, firstWorkerId);
        addParticipant(crewId, secondWorkerId);
        addParticipant(crewId, contactId);
        addParticipant(crewId, ghostId);
        jdbc.update("update conversation_participants set is_deleted = true, deleted_at = now() "
                + "where conversation_id = ? and user_id = ?", crewId, ghostId);
    }

    @AfterEach
    void tearDown() {
        Object[] ids = users.toArray();
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?) or sender_id in (?, ?, ?, ?)",
                employerId, firstWorkerId, secondWorkerId, outsiderId,
                employerId, firstWorkerId, secondWorkerId, outsiderId);
        jdbc.update("delete from notification_preferences where user_id in (?, ?, ?, ?)", ids);
        jdbc.update("delete from conversation_participants where conversation_id in (?, ?)", directId, crewId);
        jdbc.update("delete from messages where conversation_id in (?, ?)", directId, crewId);
        jdbc.update("delete from conversations where id in (?, ?)", directId, crewId);
        jdbc.update("delete from job_assignments where job_id = ?", jobId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from devices where user_id in (?, ?, ?, ?)", ids);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?)", ids);
        jdbc.update("delete from users where id in (?, ?, ?, ?, ?, ?)", employerId, firstWorkerId, secondWorkerId,
                outsiderId, contactId, ghostId);
        jdbc.update("delete from media where id = ?", employerPictureId);
    }

    @Test
    void aDirectMessageQueuesOnePushForTheCounterpartAndNoneForTheSender() throws Exception {
        String messageId = send(firstWorkerId, directId, "Yarın saat kaçta başlıyoruz?")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID sentId = UUID.fromString(objectMapper.readTree(messageId).get("data").get("id").asText());

        List<Map<String, Object>> pushes = messagePushes();
        assertEquals(1, pushes.size(), "İkili sohbette tek alıcı, tek push satırı");
        Map<String, Object> push = pushes.get(0);
        assertEquals(employerId, push.get("recipient_id"));
        assertEquals(firstWorkerId, push.get("sender_id"));
        assertEquals("QUEUED", push.get("status"));
        assertEquals("Ayşe Yılmaz", push.get("subject"));
        assertEquals("Yeni mesaj", push.get("body"));
        assertNotEquals("Yarın saat kaçta başlıyoruz?", push.get("body"), "Mesaj metni push'a girmez");
        assertNotNull(push.get("dispatch_payload"), "Outbox satırı publish payload'ını taşımalı");
        assertNotNull(push.get("next_dispatch_at"));

        Map<String, String> data = objectMapper.readValue((String) push.get("push_data_payload"),
                new TypeReference<Map<String, String>>() {
                });
        assertEquals(Map.of("type", "MESSAGE", "conversationId", directId.toString(),
                "messageId", sentId.toString()), data);
    }

    @Test
    void aRecipientWhoSwitchedMessagePushOffGetsNoPushRow() throws Exception {
        jdbc.update("insert into notification_preferences (id, user_id, category, channel, enabled, created_at, "
                + "is_deleted) values (?, ?, 'MESSAGES', 'PUSH', false, now(), false)",
                UUID.randomUUID(), employerId);

        send(firstWorkerId, directId, "sessiz").andExpect(status().isCreated());

        assertEquals(0, messagePushes().size(), "Tercihi kapalı alıcıya push satırı açılmaz");
        assertEquals(1, jdbc.queryForObject("select count(*) from messages where conversation_id = ?",
                Integer.class, directId), "Mesajın kendisi yine kaydedilir");
    }

    @Test
    void aGroupMessageReachesEveryParticipantExceptTheSender() throws Exception {
        send(employerId, crewId, "Ekip, yarın 09:00").andExpect(status().isCreated());

        List<Map<String, Object>> pushes = messagePushes();
        assertEquals(2, pushes.size(), "Üç katılımcılı grupta gönderen hariç iki push satırı");
        assertEquals(Set.of(firstWorkerId, secondWorkerId),
                Set.of(pushes.get(0).get("recipient_id"), pushes.get(1).get("recipient_id")));
    }

    @Test
    void aWorkerWhoLeftTheCrewNoLongerGetsItsPushes() throws Exception {
        jdbc.update("update conversation_participants set is_deleted = true, deleted_at = now() "
                + "where conversation_id = ? and user_id = ?", crewId, secondWorkerId);

        send(employerId, crewId, "kalanlara").andExpect(status().isCreated());

        List<Map<String, Object>> pushes = messagePushes();
        assertEquals(1, pushes.size(), "Soft delete edilen katılımcı push almaz; yalnız kalan aktif katılımcı");
        assertEquals(firstWorkerId, pushes.get(0).get("recipient_id"));
    }

    @Test
    void afterReturnsOnlyTheMessagesSentLaterInAscendingOrder() throws Exception {
        UUID first = insertMessage(directId, employerId, "1", "3 minutes");
        UUID second = insertMessage(directId, firstWorkerId, "2", "2 minutes");
        UUID third = insertMessage(directId, employerId, "3", "1 minute");

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", first.toString())
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].id").value(second.toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(third.toString()));

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", first.toString())
                        .param("size", "1")
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(second.toString()));

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", third.toString())
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(third.toString()));
    }

    @Test
    void afterDoesNotAcceptASortBecauseTheOrderIsFixed() throws Exception {
        UUID first = insertMessage(directId, employerId, "1", "1 minute");

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", first.toString())
                        .param("sort", "sentAt,desc")
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"));
    }

    @Test
    void anAfterFromAnotherConversationLooksExactlyLikeAnUnknownOne() throws Exception {
        UUID foreign = insertMessage(crewId, employerId, "grup mesajı", "1 minute");
        UUID phantom = UUID.randomUUID();

        ResultActions foreignAttempt = mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", foreign.toString())
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_AFTER_UNKNOWN"));
        ResultActions phantomAttempt = mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", phantom.toString())
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isBadRequest());

        assertEquals(envelope(phantomAttempt), envelope(foreignAttempt),
                "Başka konuşmanın mesajı ile var olmayan mesaj ayırt edilebiliyor");
    }

    @Test
    void anOutsiderAskingWithAfterStillGetsTheConversationNotFound() throws Exception {
        UUID message = insertMessage(directId, employerId, "gizli", "1 minute");

        mockMvc.perform(get("/conversations/{id}/messages", directId)
                        .param("after", message.toString())
                        .with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void aParticipantReadsASingleConversationWithTheListRowShape() throws Exception {
        insertMessage(directId, employerId, "okunmamış", "1 minute");

        mockMvc.perform(get("/conversations/{id}", directId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(directId.toString()))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.kind").value("DIRECT"))
                .andExpect(jsonPath("$.data.employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.worker.id").value(firstWorkerId.toString()))
                .andExpect(jsonPath("$.data.participants", hasSize(2)))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.closed").value(false));

        mockMvc.perform(get("/conversations/{id}", crewId).with(user(principal(secondWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("GROUP"))
                .andExpect(jsonPath("$.data.participants", hasSize(5)))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/conversations/{id}", directId).with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void aGroupReadStampsOnlyTheReadersParticipantRow() throws Exception {
        insertMessage(crewId, employerId, "Ekip, yarın 09:00", "2 minutes");
        insertMessage(crewId, secondWorkerId, "Tamam", "1 minute");

        mockMvc.perform(post("/conversations/{id}/read", crewId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk());

        Map<UUID, Object> stamps = new java.util.HashMap<>();
        jdbc.queryForList("select user_id, last_read_at from conversation_participants where conversation_id = ?",
                crewId).forEach(row -> stamps.put((UUID) row.get("user_id"), row.get("last_read_at")));
        assertNotNull(stamps.get(firstWorkerId), "okuyanın damgası yazılmalı");
        assertNull(stamps.get(employerId), "okumayanın damgasına dokunulmaz");
        assertNull(stamps.get(secondWorkerId), "okumayanın damgasına dokunulmaz");
        assertNull(stamps.get(contactId), "okumayanın damgasına dokunulmaz");

        Map<UUID, Map<String, Object>> participants = participantsSeenBy(secondWorkerId, crewId);
        assertNotNull(participants.get(firstWorkerId).get("lastReadAt"));
        assertNull(participants.get(secondWorkerId).get("lastReadAt"));
        assertEquals("EMPLOYER", participants.get(employerId).get("role"));
        assertEquals("WORKER", participants.get(firstWorkerId).get("role"));
        assertEquals("WORKER", participants.get(secondWorkerId).get("role"));
        assertEquals("COMPANY_CONTACT", participants.get(contactId).get("role"), "atamasız işveren tarafı yetkilidir");
        assertNotNull(participants.get(firstWorkerId).get("joinedAt"));
        assertEquals(false, participants.get(firstWorkerId).get("left"));
        assertEquals("İşveren", participants.get(employerId).get("firstName"));
        assertEquals("Bey", participants.get(employerId).get("lastName"));
        assertEquals("http://localhost:8080/uploads/profiles/isveren.jpg",
                participants.get(employerId).get("profilePictureUrl"));
        assertNull(participants.get(firstWorkerId).get("profilePictureUrl"));

        mockMvc.perform(post("/conversations/{id}/read", crewId).with(user(principal(secondWorkerId))))
                .andExpect(status().isOk());
        participants = participantsSeenBy(employerId, crewId);
        assertNotNull(participants.get(secondWorkerId).get("lastReadAt"),
                "read_at ilk okuyanla dolmuş olsa da ikinci okuyanın damgası kendi satırına yazılır");
        assertNotNull(participants.get(firstWorkerId).get("lastReadAt"));
        assertNull(participants.get(employerId).get("lastReadAt"));
    }

    @Test
    void aDirectReadStillMarksTheCounterpartsMessagesAndStampsTheReader() throws Exception {
        UUID theirs = insertMessage(directId, employerId, "Yarın 09:00", "2 minutes");
        UUID mine = insertMessage(directId, firstWorkerId, "Tamam", "1 minute");

        mockMvc.perform(post("/conversations/{id}/read", directId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk());

        assertNotNull(jdbc.queryForObject("select read_at from messages where id = ?", Object.class, theirs),
                "ikili sohbette karşı tarafın mesajı read_at ile işaretlenir");
        assertNull(jdbc.queryForObject("select read_at from messages where id = ?", Object.class, mine),
                "kendi mesajım işaretlenmez");
        Map<UUID, Map<String, Object>> participants = participantsSeenBy(employerId, directId);
        assertNotNull(participants.get(firstWorkerId).get("lastReadAt"));
        assertNull(participants.get(employerId).get("lastReadAt"));
        assertEquals("WORKER", participants.get(firstWorkerId).get("role"));
        assertEquals("EMPLOYER", participants.get(employerId).get("role"));

        mockMvc.perform(get("/conversations/{id}/messages", directId).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[1].id").value(theirs.toString()))
                .andExpect(jsonPath("$.data.content[1].readAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].readAt").isEmpty());
    }

    @Test
    void aParticipantWhoLeftStaysInTheListMarkedLeftSoOldMessagesKeepTheirName() throws Exception {
        UUID theirs = insertMessage(crewId, secondWorkerId, "Ben ayrılıyorum", "1 minute");
        jdbc.update("update conversation_participants set is_deleted = true, deleted_at = now() "
                + "where conversation_id = ? and user_id = ?", crewId, secondWorkerId);

        Map<UUID, Map<String, Object>> participants = participantsSeenBy(firstWorkerId, crewId);
        assertEquals(5, participants.size(), "ayrılanlar da listede");
        assertEquals(true, participants.get(secondWorkerId).get("left"));
        assertEquals("Mehmet", participants.get(secondWorkerId).get("firstName"));
        assertEquals("WORKER", participants.get(secondWorkerId).get("role"));
        assertEquals(false, participants.get(firstWorkerId).get("left"));
        assertEquals(true, participants.get(ghostId).get("left"));
        assertEquals("Anonim", participants.get(ghostId).get("firstName"), "hesabını silen katılımcı anonim adıyla");
        assertEquals("Kullanıcı", participants.get(ghostId).get("lastName"));

        mockMvc.perform(get("/conversations/{id}/messages", crewId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(theirs.toString()))
                .andExpect(jsonPath("$.data.content[0].senderId").value(secondWorkerId.toString()))
                .andExpect(jsonPath("$.data.content[0].senderName").value("Mehmet Kaya"))
                .andExpect(jsonPath("$.data.content[0].senderProfilePictureUrl").isEmpty());

        mockMvc.perform(get("/conversations/{id}", crewId).with(user(principal(secondWorkerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void aDeletedSendersMessageStaysOnThePageUnderItsAnonymisedNameAndTheBadgeAgrees() throws Exception {
        UUID ghost = insertMessage(crewId, ghostId, "silinmeden önce", "3 minutes");
        insertMessage(crewId, employerId, "duyuru", "2 minutes");
        insertMessage(crewId, secondWorkerId, "tamam", "1 minute");

        mockMvc.perform(get("/conversations/{id}", crewId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        mockMvc.perform(get("/conversations/{id}/messages", crewId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[2].id").value(ghost.toString()))
                .andExpect(jsonPath("$.data.content[2].senderId").value(ghostId.toString()))
                .andExpect(jsonPath("$.data.content[2].senderName").value("Anonim Kullanıcı"))
                .andExpect(jsonPath("$.data.content[2].senderProfilePictureUrl").isEmpty())
                .andExpect(jsonPath("$.data.content[1].senderName").value("İşveren Bey"));

        mockMvc.perform(get("/conversations/{id}/messages", crewId)
                        .param("after", ghost.toString())
                        .with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].senderName").value("İşveren Bey"))
                .andExpect(jsonPath("$.data.content[1].senderName").value("Mehmet Kaya"));
    }

    @Test
    void messagesCarryTheSenderNameAndPicture() throws Exception {
        insertMessage(directId, employerId, "Merhaba", "1 minute");

        mockMvc.perform(get("/conversations/{id}/messages", directId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].senderId").value(employerId.toString()))
                .andExpect(jsonPath("$.data.content[0].senderName").value("İşveren Bey"))
                .andExpect(jsonPath("$.data.content[0].senderProfilePictureUrl")
                        .value("http://localhost:8080/uploads/profiles/isveren.jpg"));

        send(firstWorkerId, directId, "Selam")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.senderName").value("Ayşe Yılmaz"))
                .andExpect(jsonPath("$.data.senderProfilePictureUrl").isEmpty());
    }

    @Test
    void anOutsiderMarkingReadGetsTheSameNotFoundAndLeavesNoStamp() throws Exception {
        mockMvc.perform(post("/conversations/{id}/read", crewId).with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));

        assertEquals(0, jdbc.queryForObject("select count(*) from conversation_participants "
                + "where conversation_id = ? and last_read_at is not null", Integer.class, crewId));
    }

    private Map<UUID, Map<String, Object>> participantsSeenBy(UUID viewerId, UUID conversationId) throws Exception {
        String body = mockMvc.perform(get("/conversations/{id}", conversationId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> rows = objectMapper.convertValue(
                objectMapper.readTree(body).get("data").get("participants"),
                new TypeReference<List<Map<String, Object>>>() {
                });
        Map<UUID, Map<String, Object>> byId = new java.util.HashMap<>();
        rows.forEach(row -> byId.put(UUID.fromString((String) row.get("id")), row));
        return byId;
    }

    private List<Map<String, Object>> messagePushes() {
        return jdbc.queryForList("select recipient_id, sender_id, status, subject, body, push_data_payload, "
                + "dispatch_payload, next_dispatch_at from notifications "
                + "where channel = 'PUSH' and category = 'MESSAGES' and recipient_id in (?, ?, ?, ?) "
                + "order by created_at", employerId, firstWorkerId, secondWorkerId, outsiderId);
    }

    private UUID insertMessage(UUID conversationId, UUID senderId, String body, String ago) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into messages (id, created_at, is_deleted, conversation_id, sender_id, body, sent_at) "
                + "values (?, now() - cast(? as interval), false, ?, ?, ?, now() - cast(? as interval))",
                id, ago, conversationId, senderId, body, ago);
        return id;
    }

    private ResultActions send(UUID actorId, UUID conversationId, String body) throws Exception {
        return mockMvc.perform(post("/conversations/{id}/messages", conversationId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", body))));
    }

    private String envelope(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed = new TreeMap<>(objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString();
    }

    private void addParticipant(UUID conversationId, UUID userId) {
        jdbc.update("insert into conversation_participants (id, created_at, is_deleted, conversation_id, user_id) "
                + "values (?, now(), false, ?, ?)", UUID.randomUUID(), conversationId, userId);
    }

    private void insertUser(UUID id, String firstName, String lastName) {
        jdbc.update("insert into users (id, created_at, is_deleted, first_name, last_name, email, password, "
                        + "is_employer, email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, ?, ?, 'test', false, true, false, false)",
                id, firstName, lastName, id + "@example.com");
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
