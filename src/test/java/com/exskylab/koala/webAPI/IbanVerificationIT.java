package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.StaffTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IbanVerificationIT extends AbstractStaffIntegrationTest {

    private static final String OWN_IBAN = "TR18 0006 2001 1900 0006 6723 15";
    private static final String OWN_IBAN_NORMALISED = "TR180006200119000006672315";
    private static final String SOMEONE_ELSES_IBAN = "TR96 0001 0002 0030 0040 0050 06";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProducerService userProducerService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, true)",
                userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'iban-verification-%@iskoala.com'");
        jdbcTemplate.update("delete from bank_accounts where user_id = ?", userId);
        jdbcTemplate.update("delete from authorities where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(userId, userId + "@example.com", Set.of(Role.ROLE_USER), null);
    }

    private String staffToken(String... roles) {
        String subject = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into staff_members (id, keycloak_subject, email, display_name, is_active, created_at)
                values (?, ?, ?, ?, true, now())
                """, UUID.randomUUID(), subject, "iban-verification-" + subject + "@iskoala.com", "Finans");
        return StaffTokenIssuer.tokenFor(subject, List.of(roles));
    }

    private UUID addAccount(String iban) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/me/bank-accounts")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + iban + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("id").asString());
    }

    private JsonNode startVerification(UUID accountId) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/me/bank-accounts/{accountId}/verification", accountId)
                        .with(user(principal())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private String confirmBody(String senderIban, String amount) {
        return "{\"senderIban\":\"" + senderIban + "\",\"amount\":" + amount + "}";
    }

    private String storedStatus(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "select verification_status from bank_accounts where id = ?", String.class, accountId);
    }

    @Test
    void aFreshAccountIsAskedForASmallTransferFromItsOwnIban() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);

        JsonNode verification = startVerification(accountId);

        assertEquals("PENDING", verification.get("status").asString());
        assertTrue(verification.get("reference").asString().startsWith("KOALA-"));
        assertEquals("TR18***2315", verification.get("maskedIban").asString());
        assertEquals("TR330006100519786457841326", verification.get("targetIban").asString());
        assertTrue(verification.get("amount").decimalValue().signum() > 0);
        assertEquals(4, verification.get("attemptsLeft").asInt());
        assertEquals("PENDING", storedStatus(accountId));
    }

    @Test
    void theInstructionsNeverEchoTheRawIbanOfTheAccountBeingVerified() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);

        mockMvc.perform(post("/users/me/bank-accounts/{accountId}/verification", accountId)
                        .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(OWN_IBAN_NORMALISED))));
    }

    @Test
    void aTransferSentFromAnotherIbanDoesNotVerifyTheAccount() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode verification = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        verification.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(SOMEONE_ELSES_IBAN, verification.get("amount").asString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BANK_ACCOUNT_VERIFICATION_SENDER_MISMATCH"));

        assertNotEquals("VERIFIED", storedStatus(accountId));
    }

    @Test
    void aTransferSentFromTheRegisteredIbanVerifiesTheAccount() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode verification = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        verification.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(OWN_IBAN, verification.get("amount").asString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        assertEquals("VERIFIED", storedStatus(accountId));

        mockMvc.perform(get("/users/me/bank-accounts").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].verificationStatus").value("VERIFIED"));
    }

    @Test
    void aFailedAttemptCanBeStartedAgainAndThenSucceed() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode first = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        first.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(SOMEONE_ELSES_IBAN, first.get("amount").asString())))
                .andExpect(status().isBadRequest());

        JsonNode second = startVerification(accountId);
        assertNotEquals(first.get("reference").asString(), second.get("reference").asString());
        assertEquals(3, second.get("attemptsLeft").asInt());

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        second.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(OWN_IBAN, second.get("amount").asString())))
                .andExpect(status().isOk());

        assertEquals("VERIFIED", storedStatus(accountId));
    }

    @Test
    void aVerifiedAccountIsNotSentBackToTheBank() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode verification = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        verification.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(OWN_IBAN, verification.get("amount").asString())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users/me/bank-accounts/{accountId}/verification", accountId)
                        .with(user(principal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BANK_ACCOUNT_VERIFICATION_ALREADY_VERIFIED"));
    }

    @Test
    void staffWithoutTheFinanceDutyCannotConfirmATransfer() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode verification = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        verification.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("DISPUTE_HANDLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(OWN_IBAN, verification.get("amount").asString())))
                .andExpect(status().isForbidden());

        assertNotEquals("VERIFIED", storedStatus(accountId));
    }

    @Test
    void changingTheIbanThroughTheProfileTakesTheVerificationDown() throws Exception {
        UUID accountId = addAccount(OWN_IBAN);
        JsonNode verification = startVerification(accountId);

        mockMvc.perform(post("/admin/bank-account-verifications/{reference}/confirm",
                        verification.get("reference").asString())
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(OWN_IBAN, verification.get("amount").asString())))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/users/me")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + SOMEONE_ELSES_IBAN + "\"}"))
                .andExpect(status().isOk());

        assertEquals("UNVERIFIED", storedStatus(accountId));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "select verification_attempts from bank_accounts where id = ?", Integer.class, accountId));
    }
}
