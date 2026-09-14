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
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID directId = UUID.randomUUID();
    private final UUID crewId = UUID.randomUUID();
    private final List<UUID> users = List.of(employerId, firstWorkerId, secondWorkerId, outsiderId);

    @BeforeEach
    void setUp() {
        insertUser(employerId, "İşveren", "Bey");
        insertUser(firstWorkerId, "Ayşe", "Yılmaz");
        insertUser(secondWorkerId, "Mehmet", "Kaya");
        insertUser(outsiderId, "Yabancı", "Kişi");
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
        jdbc.update("delete from users where id in (?, ?, ?, ?)", ids);
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
                .andExpect(jsonPath("$.data.participants", hasSize(3)))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/conversations/{id}", directId).with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));
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
