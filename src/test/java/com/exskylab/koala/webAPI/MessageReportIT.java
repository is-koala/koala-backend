package com.exskylab.koala.webAPI;

import com.zaxxer.hikari.HikariDataSource;
import com.exskylab.koala.business.concretes.MessagingManager;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MessageReportIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private MessagingManager messagingManager;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID otherConversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId);
        insertUser(workerId);
        insertUser(outsiderId);
        jdbc.update("""
                insert into jobs
                    (id, created_at, is_deleted, creator_id, title, status, employer_type,
                     experience_required, insurance_required, min_age, max_age,
                     start_date_time, end_date_time)
                values (?, now(), false, ?, 'Mesaj şikâyeti testi', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now(), now() + interval '1 day')
                """, jobId, employerId);
        jdbc.update("""
                insert into conversations
                    (id, created_at, is_deleted, job_id, employer_id, worker_id, kind)
                values (?, now(), false, ?, ?, ?, 'DIRECT')
                """, conversationId, jobId, employerId, workerId);
        addParticipant(employerId);
        addParticipant(workerId);
        jdbc.update("""
                insert into messages
                    (id, created_at, is_deleted, conversation_id, sender_id, body, sent_at)
                values (?, now(), false, ?, ?, 'şikâyet edilebilir mesaj', now())
                """, messageId, conversationId, workerId);
        jdbc.update("""
                insert into media
                    (id, created_at, is_deleted, media_type, file_name, file_type, file_url, file_size)
                values (?, now(), false, 'IMAGE', 'şikâyet.jpg', 'image/jpeg', 'reports/test.jpg', 128)
                """, imageId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from reports where message_id = ?", messageId);
        jdbc.update("delete from media where id = ?", imageId);
        jdbc.update("delete from messages where id = ?", messageId);
        jdbc.update("delete from conversation_participants where conversation_id in (?, ?)",
                conversationId, otherConversationId);
        jdbc.update("delete from conversations where id in (?, ?)", conversationId, otherConversationId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", employerId, workerId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, workerId, outsiderId);
    }

    @Test
    void aNonParticipantCannotReportAnExistingMessage() throws Exception {
        UUID missingMessageId = UUID.randomUUID();
        String missing = errorEnvelope(report(missingMessageId, outsiderId), missingMessageId);
        String existing = errorEnvelope(report(messageId, outsiderId), messageId);

        assertEquals(missing, existing);
        assertEquals(0, reportCount());
    }

    @Test
    void aParticipantReportsTheSenderDerivedFromTheStoredMessage() throws Exception {
        report(messageId, employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportedUserId").value(workerId.toString()))
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()));

        assertEquals(workerId, jdbc.queryForObject(
                "select reported_user_id from reports where message_id = ?", UUID.class, messageId));
    }

    @Test
    void aLegacyMessageReportMayCarryOtherTargetsWithoutTrustingItsReportedUser() throws Exception {
        report(legacyBody(jobId, imageId, outsiderId), employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportedUserId").value(workerId.toString()))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.imageId").value(imageId.toString()));
        UUID firstReportId = jdbc.queryForObject(
                "select id from reports where message_id = ?", UUID.class, messageId);

        report(legacyBody(jobId, imageId, employerId), employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(firstReportId.toString()))
                .andExpect(jsonPath("$.data.reportedUserId").value(workerId.toString()));

        assertEquals(1, reportCount());
    }

    @Test
    void changingALegacyJobTargetOnRetryConflicts() throws Exception {
        report(legacyBody(jobId, imageId, outsiderId), employerId).andExpect(status().isCreated());

        report(legacyBody(null, imageId, outsiderId), employerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_IDEMPOTENCY_CONFLICT"));

        assertEquals(1, reportCount());
    }

    @Test
    void changingALegacyImageTargetOnRetryConflicts() throws Exception {
        report(legacyBody(jobId, imageId, outsiderId), employerId).andExpect(status().isCreated());

        report(legacyBody(jobId, null, outsiderId), employerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_IDEMPOTENCY_CONFLICT"));

        assertEquals(1, reportCount());
    }

    @Test
    void retryAfterALostResponseReturnsTheSameReport() throws Exception {
        report(messageId, employerId, "HARASSMENT", "  rahatsız edici  ").andExpect(status().isCreated());
        UUID firstReportId = jdbc.queryForObject(
                "select id from reports where message_id = ?", UUID.class, messageId);

        report(messageId, employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(firstReportId.toString()));

        assertEquals(1, reportCount());
    }

    @Test
    void retryOfALegacyMessageOnlyReportCanonicalizesItsStoredSender() throws Exception {
        UUID legacyReportId = UUID.randomUUID();
        jdbc.update("""
                insert into reports
                    (id, created_at, is_deleted, reporter_id, reported_user_id, message_id,
                     reason, description, status)
                values (?, now(), false, ?, null, ?, 'HARASSMENT', 'rahatsız edici', 'OPEN')
                """, legacyReportId, employerId, messageId);

        report(messageId, employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(legacyReportId.toString()))
                .andExpect(jsonPath("$.data.reportedUserId").value(workerId.toString()));

        assertEquals(workerId, jdbc.queryForObject(
                "select reported_user_id from reports where id = ?", UUID.class, legacyReportId));
        assertEquals(1, reportCount());
    }

    @Test
    void concurrentRetriesReturnOnePersistedReport() throws Exception {
        CountDownLatch firstHasMessageLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean holdFirstLookup = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object message = invocation.callRealMethod();
            if (holdFirstLookup.compareAndSet(true, false)) {
                firstHasMessageLock.countDown();
                await(releaseFirst);
            }
            return message;
        }).when(messagingManager).getMessageForReport(messageId, employerId);

        HikariDataSource pool = (HikariDataSource) dataSource;
        try (Connection observer = DriverManager.getConnection(
                pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
             var executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = executor.submit(() -> responseId(
                    report(messageId, employerId).andExpect(status().isCreated())));
            assertTrue(firstHasMessageLock.await(10, TimeUnit.SECONDS),
                    "İlk retry mesaj kilidini aldıktan sonra idempotency aramasına ulaşmalı");
            Future<UUID> second = executor.submit(() -> responseId(
                    report(messageId, employerId).andExpect(status().isCreated())));
            try {
                awaitSecondRequestOnTheMessageLock(observer, first, second);
            } finally {
                releaseFirst.countDown();
            }

            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
        }

        assertEquals(1, reportCount());
    }

    @Test
    void aRetryStillReturnsTheSameReportAfterModeration() throws Exception {
        report(messageId, employerId).andExpect(status().isCreated());
        UUID firstReportId = jdbc.queryForObject(
                "select id from reports where message_id = ?", UUID.class, messageId);
        jdbc.update("update reports set status = 'ACTIONED', reviewed_at = now() where id = ?", firstReportId);

        report(messageId, employerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(firstReportId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIONED"));

        assertEquals(1, reportCount());
    }

    @Test
    void reusingTheMessageWithDifferentReportContentConflicts() throws Exception {
        report(messageId, employerId).andExpect(status().isCreated());

        report(messageId, employerId, "FRAUD", "başka şikâyet")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_IDEMPOTENCY_CONFLICT"));

        assertEquals(1, reportCount());
    }

    @Test
    void databaseRejectsASecondActiveReporterMessagePair() throws Exception {
        report(messageId, employerId).andExpect(status().isCreated());

        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                insert into reports
                    (id, created_at, is_deleted, reporter_id, reported_user_id, message_id,
                     reason, description, status)
                values (?, now(), false, ?, ?, ?, 'SPAM', 'doğrudan tekrar', 'OPEN')
                """, UUID.randomUUID(), employerId, workerId, messageId));
        assertEquals(1, reportCount());
    }

    @Test
    void aSenderCannotReportTheirOwnMessage() throws Exception {
        report(messageId, workerId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REPORT_CANNOT_REPORT_SELF"));

        assertEquals(0, reportCount());
    }

    @Test
    void aSoftDeletedMessageIsConcealed() throws Exception {
        jdbc.update("update messages set is_deleted = true, deleted_at = now() where id = ?", messageId);

        report(messageId, employerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_MESSAGE_NOT_FOUND"));

        assertEquals(0, reportCount());
    }

    @Test
    void aMessageInASoftDeletedConversationIsConcealed() throws Exception {
        jdbc.update("update conversations set is_deleted = true, deleted_at = now() where id = ?", conversationId);

        report(messageId, employerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_MESSAGE_NOT_FOUND"));

        assertEquals(0, reportCount());
    }

    @Test
    void aSoftDeletedParticipantMembershipIsConcealed() throws Exception {
        jdbc.update("""
                update conversation_participants set is_deleted = true, deleted_at = now()
                where conversation_id = ? and user_id = ?
                """, conversationId, employerId);

        report(messageId, employerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_MESSAGE_NOT_FOUND"));

        assertEquals(0, reportCount());
    }

    @Test
    void membershipInAnotherConversationDoesNotAuthorizeTheMessage() throws Exception {
        jdbc.update("""
                insert into conversations
                    (id, created_at, is_deleted, job_id, employer_id, worker_id, kind)
                values (?, now(), false, ?, ?, null, 'GROUP')
                """, otherConversationId, jobId, employerId);
        jdbc.update("""
                insert into conversation_participants
                    (id, created_at, is_deleted, conversation_id, user_id, joined_at)
                values (?, now(), false, ?, ?, now())
                """, UUID.randomUUID(), otherConversationId, outsiderId);

        report(messageId, outsiderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_MESSAGE_NOT_FOUND"));

        assertEquals(0, reportCount());
    }

    private ResultActions report(UUID targetMessageId, UUID reporterId) throws Exception {
        return report(targetMessageId, reporterId, "HARASSMENT", "rahatsız edici");
    }

    private ResultActions report(UUID targetMessageId, UUID reporterId, String reason, String description)
            throws Exception {
        return report("""
                {"messageId":"%s","reason":"%s","description":"%s"}
                """.formatted(targetMessageId, reason, description), reporterId);
    }

    private ResultActions report(String body, UUID reporterId) throws Exception {
        return mockMvc.perform(post("/reports")
                .with(user(principal(reporterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID responseId(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {
        });
        Map<?, ?> data = (Map<?, ?>) envelope.get("data");
        return UUID.fromString((String) data.get("id"));
    }

    private String legacyBody(UUID targetJobId, UUID targetImageId, UUID clientReportedUserId) {
        return """
                {"messageId":"%s","reportedUserId":"%s","jobId":%s,"imageId":%s,
                 "reason":"HARASSMENT","description":"rahatsız edici"}
                """.formatted(messageId, clientReportedUserId, jsonId(targetJobId), jsonId(targetImageId));
    }

    private String jsonId(UUID id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private static void awaitSecondRequestOnTheMessageLock(
            Connection observer, Future<UUID> first, Future<UUID> second)
            throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            assertFalse(first.isDone() || second.isDone(),
                    "İlk transaction bırakmadan iki retry da tamamlanmamalı");
            if (waitingLockSessions(observer) >= 1) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("İkinci retry ilk transaction'ın kalıcı mesaj kilidinde beklemedi");
    }

    private static int waitingLockSessions(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select count(*) from pg_stat_activity
                     where datname = current_database()
                       and pid <> pg_backend_pid()
                       and wait_event_type = 'Lock'
                       and query ilike '%messages%'
                     """)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("message report lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("message report lock test interrupted", exception);
        }
    }

    private String errorEnvelope(ResultActions actions, UUID resourceId) throws Exception {
        String body = actions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_MESSAGE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Object> parsed = new TreeMap<>(envelope);
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString().replace(resourceId.toString(), "{message-id}");
    }

    private int reportCount() {
        return jdbc.queryForObject("select count(*) from reports where message_id = ?", Integer.class, messageId);
    }

    private void addParticipant(UUID userId) {
        jdbc.update("""
                insert into conversation_participants
                    (id, created_at, is_deleted, conversation_id, user_id, joined_at)
                values (?, now(), false, ?, ?, now())
                """, UUID.randomUUID(), conversationId, userId);
    }

    private void insertUser(UUID id) {
        jdbc.update("""
                insert into users
                    (id, created_at, is_deleted, email, password, is_employer,
                     email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, false)
                """, id, email(id));
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }

    private String email(UUID id) {
        return id + "@example.test";
    }
}
