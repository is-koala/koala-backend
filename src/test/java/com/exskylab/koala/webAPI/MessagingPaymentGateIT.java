package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.SettlementTask;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MessagingPaymentGateIT extends AbstractIntegrationTest {

    private static final BigDecimal WORKER_PAYOUT = new BigDecimal("1000.00");
    private static final BigDecimal TOTAL_CHARGED = new BigDecimal("1075.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyContactService companyContactService;

    @MockitoBean
    private IyzicoPaymentService iyzicoPaymentService;

    @MockitoBean
    private SettlementService settlementService;

    @MockitoBean
    private ActivityNotificationService activityNotificationService;

    private final UUID employerId = UUID.randomUUID();
    private final UUID officerId = UUID.randomUUID();
    private final UUID lateOfficerId = UUID.randomUUID();
    private final UUID firstWorkerId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID firstAssignmentId = UUID.randomUUID();
    private final UUID secondAssignmentId = UUID.randomUUID();
    private final UUID firstPaymentId = UUID.randomUUID();
    private final UUID secondPaymentId = UUID.randomUUID();
    private final String firstToken = "token-" + firstAssignmentId;
    private final String secondToken = "token-" + secondAssignmentId;

    @BeforeEach
    void setUp() {
        for (UUID id : List.of(employerId, officerId, lateOfficerId, firstWorkerId, secondWorkerId, outsiderId)) {
            insertUser(id);
        }
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Mesaj Şirketi " + companyId,
                String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        insertContact(employerId);
        insertContact(officerId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Mesaj kapısı", "MESSAGING_GATE_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, 'COMPANY', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 2, now() + interval '3 days', "
                        + "now() + interval '4 days', 'v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)",
                jobId, employerId, companyId, categoryId, "Mesaj kapısı " + jobId);
        insertUnpaidAssignment(firstAssignmentId, firstWorkerId, firstPaymentId, firstToken);
        insertUnpaidAssignment(secondAssignmentId, secondWorkerId, secondPaymentId, secondToken);

        when(iyzicoPaymentService.retrieveCheckoutFormResult(any(IyzicoPaymentResultRequestDto.class)))
                .thenAnswer(call -> providerSuccessFor(call.getArgument(0)));
        when(settlementService.enqueueRefund(any(), any(), any())).thenReturn(Optional.of(new SettlementTask()));
        when(settlementService.enqueue(any(), any(), any())).thenReturn(Optional.of(new SettlementTask()));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from conversation_participants where conversation_id in "
                + "(select id from conversations where job_id = ?)", jobId);
        jdbc.update("delete from messages where conversation_id in (select id from conversations where job_id = ?)",
                jobId);
        jdbc.update("delete from conversations where job_id = ?", jobId);
        jdbc.update("delete from cancellations where job_assignment_id in (?, ?)",
                firstAssignmentId, secondAssignmentId);
        jdbc.update("delete from refunds where payment_id in (?, ?)", firstPaymentId, secondPaymentId);
        jdbc.update("delete from payments where id in (?, ?)", firstPaymentId, secondPaymentId);
        jdbc.update("delete from job_assignments where id in (?, ?)", firstAssignmentId, secondAssignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        List<UUID> users = List.of(employerId, officerId, lateOfficerId, firstWorkerId, secondWorkerId, outsiderId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?, ?, ?)", users.toArray());
        jdbc.update("delete from users where id in (?, ?, ?, ?, ?, ?)", users.toArray());
    }

    @Test
    void nobodyCanOpenTheConversationBeforeTheEmployerPays() throws Exception {
        openConversation(firstWorkerId, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_PAYMENT_REQUIRED"));

        openConversation(employerId, firstWorkerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_PAYMENT_REQUIRED"));

        openConversation(officerId, firstWorkerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_PAYMENT_REQUIRED"));

        assertTrue(crewId().isEmpty(), "Ödeme öncesi grup kurulmamalı");
        assertEquals(0, countConversations(), "Ödeme öncesi hiçbir konuşma açılmamalı");
    }

    @Test
    void anAcceptedAssignmentAloneDoesNotOpenMessagingAndTheAssignmentSaysWhy() throws Exception {
        mockMvc.perform(get("/job-assignments/{id}", firstAssignmentId).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING_COLLECTION"))
                .andExpect(jsonPath("$.data.messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("PAYMENT_REQUIRED"));
    }

    @Test
    void settlingThePaymentBuildsTheCrewAndOpensTheDirectConversation() throws Exception {
        settle(firstToken);

        assertEquals("HELD_IN_ESCROW", paymentStatusOf(firstAssignmentId));
        UUID crew = crewId().orElseThrow();
        assertEquals(Set.of(employerId, officerId, firstWorkerId), participantsOf(crew),
                "Grup ilan sahibi, şirket yetkilisi ve ödenen işçiyle kurulmalı");

        openConversation(firstWorkerId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("DIRECT"))
                .andExpect(jsonPath("$.data.closed").value(false))
                .andExpect(jsonPath("$.data.worker.id").value(firstWorkerId.toString()));

        UUID direct = directId(firstWorkerId).orElseThrow();
        send(firstWorkerId, direct, "Yarın 09:00'da oradayım")
                .andExpect(status().isCreated());
        send(employerId, crew, "Ekibe hoş geldiniz")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/job-assignments/{id}", firstAssignmentId).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messagingAvailable").value(true))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("AVAILABLE"));
    }

    @Test
    void theSecondPaidWorkerJoinsTheSameCrewInsteadOfOpeningANewOne() throws Exception {
        settle(firstToken);
        settle(secondToken);

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from conversations where job_id = ? and kind = 'GROUP' and is_deleted = false",
                Integer.class, jobId));
        assertEquals(Set.of(employerId, officerId, firstWorkerId, secondWorkerId), participantsOf(crewId().orElseThrow()));
    }

    @Test
    void theSecondWorkerCannotMessageUntilTheirOwnAssignmentIsPaid() throws Exception {
        settle(firstToken);

        openConversation(secondWorkerId, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_PAYMENT_REQUIRED"));
        assertFalse(participantsOf(crewId().orElseThrow()).contains(secondWorkerId));
    }

    @Test
    void aCompanyOfficerReadsAndWritesInTheCrew() throws Exception {
        settle(firstToken);
        UUID crew = crewId().orElseThrow();
        send(firstWorkerId, crew, "Merhaba ekip").andExpect(status().isCreated());

        mockMvc.perform(get("/conversations/{id}/messages", crew).with(user(principal(officerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].body").value("Merhaba ekip"));
        send(officerId, crew, "Hoş geldin").andExpect(status().isCreated());

        mockMvc.perform(get("/conversations").with(user(principal(officerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + crew + "')].closed").value(false));
    }

    @Test
    void anOfficerAddedLaterJoinsTheOpenCrewAndARemovedOneLeavesIt() throws Exception {
        settle(firstToken);
        UUID crew = crewId().orElseThrow();

        companyContactService.addCompanyContact(companyReference(), userReference(lateOfficerId),
                CompanyContactRole.STAFF);
        assertTrue(participantsOf(crew).contains(lateOfficerId), "Sonradan eklenen yetkili açık gruba katılmalı");

        mockMvc.perform(delete("/companies/{companyId}/contacts/{userId}", companyId, officerId)
                        .with(user(principal(employerId))))
                .andExpect(status().is2xxSuccessful());
        assertFalse(participantsOf(crew).contains(officerId), "Çıkarılan yetkili gruptan da çıkmalı");
        mockMvc.perform(get("/conversations/{id}/messages", crew).with(user(principal(officerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void theSevenDayWindowStillClosesAPaidConversation() throws Exception {
        settle(firstToken);
        openConversation(firstWorkerId, null).andExpect(status().isOk());
        UUID direct = directId(firstWorkerId).orElseThrow();
        jdbc.update("update jobs set end_date_time = now() - interval '8 days' where id = ?", jobId);

        openConversation(firstWorkerId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_WINDOW_CLOSED"));
        send(firstWorkerId, direct, "hala buradayım")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_WINDOW_CLOSED"));
        send(employerId, crewId().orElseThrow(), "ekip")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_WINDOW_CLOSED"));
        mockMvc.perform(get("/job-assignments/{id}", firstAssignmentId).with(user(principal(firstWorkerId))))
                .andExpect(jsonPath("$.data.messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("CLOSED"));
    }

    @Test
    void anOutsiderStillGetsTheSameNotFoundBeforeAndAfterPayment() throws Exception {
        String before = errorEnvelope(openConversation(outsiderId, firstWorkerId).andExpect(status().isNotFound()));

        settle(firstToken);

        String after = errorEnvelope(openConversation(outsiderId, firstWorkerId).andExpect(status().isNotFound()));
        assertEquals(before, after, "Ödeme, ilanla ilişkisi olmayan kullanıcının yanıtını değiştirmemeli");
        assertTrue(before.contains("MESSAGING_NOT_A_PARTICIPANT"), before);
    }

    @Test
    void cancellingThePaidAssignmentClosesTheDirectChatAndRemovesTheWorkerFromTheCrew() throws Exception {
        settle(firstToken);
        settle(secondToken);
        openConversation(firstWorkerId, null).andExpect(status().isOk());
        UUID direct = directId(firstWorkerId).orElseThrow();
        UUID crew = crewId().orElseThrow();
        send(firstWorkerId, direct, "iptalden önce").andExpect(status().isCreated());

        mockMvc.perform(post("/job-assignments/{id}/cancel", firstAssignmentId)
                        .with(user(principal(employerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"iş iptal\"}"))
                .andExpect(status().isCreated());

        assertEquals("CANCELLED", jdbc.queryForObject("select status from job_assignments where id = ?",
                String.class, firstAssignmentId));
        assertEquals("REFUNDED", paymentStatusOf(firstAssignmentId));
        assertEquals(Set.of(employerId, officerId, secondWorkerId), participantsOf(crew),
                "İptal edilen işçi gruptan çıkmalı, diğerleri kalmalı");

        send(firstWorkerId, direct, "iptalden sonra")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_WINDOW_CLOSED"));
        mockMvc.perform(get("/conversations/{id}/messages", direct).with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].body").value("iptalden önce"));
        mockMvc.perform(get("/conversations").with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + direct + "')].closed").value(true));
        mockMvc.perform(get("/conversations/{id}/messages", crew).with(user(principal(firstWorkerId))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/job-assignments/{id}", firstAssignmentId).with(user(principal(firstWorkerId))))
                .andExpect(jsonPath("$.data.messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("CLOSED"));

        send(secondWorkerId, crew, "ben hâlâ ekipteyim").andExpect(status().isCreated());
    }

    @Test
    void aDirectConversationLeftOverFromBeforeThePaymentGateIsReadOnly() throws Exception {
        UUID legacy = UUID.randomUUID();
        jdbc.update("insert into conversations (id, created_at, is_deleted, job_id, employer_id, worker_id, kind) "
                + "values (?, now(), false, ?, ?, ?, 'DIRECT')", legacy, jobId, employerId, firstWorkerId);
        for (UUID member : List.of(employerId, firstWorkerId)) {
            jdbc.update("insert into conversation_participants (id, created_at, is_deleted, conversation_id, user_id) "
                    + "values (?, now(), false, ?, ?)", UUID.randomUUID(), legacy, member);
        }

        mockMvc.perform(get("/conversations").with(user(principal(firstWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + legacy + "')].closed").value(true));
        send(employerId, legacy, "ödeme öncesi mesaj")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_PAYMENT_REQUIRED"));

        settle(firstToken);

        mockMvc.perform(get("/conversations").with(user(principal(firstWorkerId))))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + legacy + "')].closed").value(false));
        send(employerId, legacy, "ödeme sonrası mesaj").andExpect(status().isCreated());
    }

    private ResultActions openConversation(UUID actorId, UUID workerId) throws Exception {
        var request = post("/jobs/{jobId}/conversation", jobId).with(user(principal(actorId)));
        if (workerId != null) {
            request = request.param("workerId", workerId.toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(UUID actorId, UUID conversationId, String body) throws Exception {
        return mockMvc.perform(post("/conversations/{id}/messages", conversationId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", body))));
    }

    private void settle(String token) throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", token))
                .andExpect(status().is3xxRedirection());
    }

    private IyzicoPaymentResultResponseDto providerSuccessFor(IyzicoPaymentResultRequestDto request) {
        Map<String, Object> payment = jdbc.queryForMap(
                "select p.conversation_id, p.provider_submerchant_key, p.job_assignment_id "
                        + "from payments p where p.provider_token = ?", request.getToken());
        IyzicoPaymentResultResponseDto result = new IyzicoPaymentResultResponseDto();
        result.setStatus("success");
        result.setPaymentStatus("SUCCESS");
        result.setFraudStatus(1);
        result.setMdStatus(1);
        result.setPaymentId("pay-" + request.getToken());
        result.setPaymentTransactionId("txn-" + request.getToken());
        result.setConversationId((String) payment.get("conversation_id"));
        result.setToken(request.getToken());
        result.setBasketId(payment.get("job_assignment_id").toString());
        result.setPrice(TOTAL_CHARGED);
        result.setPaidPrice(TOTAL_CHARGED);
        result.setItemCount(1);
        result.setItemId(jobId.toString());
        result.setItemPrice(TOTAL_CHARGED);
        result.setItemPaidPrice(TOTAL_CHARGED);
        result.setItemSubMerchantPrice(WORKER_PAYOUT);
        result.setItemSubMerchantKey((String) payment.get("provider_submerchant_key"));
        result.setCurrency("TRY");
        return result;
    }

    private void insertUnpaidAssignment(UUID assignmentId, UUID workerId, UUID paymentId, String token) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, 'CONFIRMED', 'PENDING_COLLECTION', now(), 0, 1000.00, "
                        + "0.0750, 75.00, 1075.00, 'v1', 'TRY')",
                assignmentId, jobId, workerId);
        jdbc.update("insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status, "
                        + "currency, pricing_version, worker_payout, commission_rate, commission_amount, "
                        + "total_charged, provider_token, provider_submerchant_key) "
                        + "values (?, now(), false, ?, ?, 'INITIATED', 'TRY', 'v1', 1000.00, 0.0750, 75.00, "
                        + "1075.00, ?, ?)",
                paymentId, assignmentId, "conv-" + paymentId, token, "submerchant-" + workerId);
    }

    private void insertContact(UUID userId) {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                + "values (?, now(), false, ?, ?, 'ADMIN')", UUID.randomUUID(), companyId, userId);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                id, id + "@example.com");
    }

    private Company companyReference() {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }

    private User userReference(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private String paymentStatusOf(UUID assignmentId) {
        return jdbc.queryForObject("select payment_status from job_assignments where id = ?",
                String.class, assignmentId);
    }

    private int countConversations() {
        return jdbc.queryForObject("select count(*) from conversations where job_id = ?", Integer.class, jobId);
    }

    private Optional<UUID> crewId() {
        return jdbc.queryForList("select id from conversations where job_id = ? and kind = 'GROUP' "
                + "and is_deleted = false", UUID.class, jobId).stream().findFirst();
    }

    private Optional<UUID> directId(UUID workerId) {
        return jdbc.queryForList("select id from conversations where job_id = ? and worker_id = ? "
                + "and kind = 'DIRECT' and is_deleted = false", UUID.class, jobId, workerId).stream().findFirst();
    }

    private Set<UUID> participantsOf(UUID conversationId) {
        return Set.copyOf(jdbc.queryForList("select user_id from conversation_participants "
                + "where conversation_id = ? and is_deleted = false", UUID.class, conversationId));
    }

    private String errorEnvelope(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);
        return node.get("httpStatus").asText() + " " + node.get("errorCode").asText() + " "
                + node.get("errorCategory").asText() + " " + node.get("message").asText();
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
