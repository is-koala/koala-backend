package com.exskylab.koala.webAPI;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.exceptions.GlobalExceptionHandler;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.producers.user.UserProducerService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@AutoConfigureMockMvc
class BankAccountIT extends AbstractIntegrationTest {

    private static final String FIRST_IBAN = "TR18 0006 2001 1900 0006 6723 15";
    private static final String SECOND_IBAN = "TR96 0001 0002 0030 0040 0050 06";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProducerService userProducerService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(userId);
        insertUser(otherUserId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from bank_accounts where user_id in (?, ?)", userId, otherUserId);
        jdbcTemplate.update("delete from authorities where user_id in (?, ?)", userId, otherUserId);
        jdbcTemplate.update("delete from users where id in (?, ?)", userId, otherUserId);
    }

    @Test
    void theFirstAccountIsPrimaryAndOnlyMaskedDataLeavesTheHttpInterface() throws Exception {
        mockMvc.perform(post("/users/me/bank-accounts")
                        .with(user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + FIRST_IBAN + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data.primary").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("UNVERIFIED"))
                .andExpect(content().string(not(containsString("TR180006200119000006672315"))));

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data[0].primary").value(true));
    }

    @Test
    void theUserCanSelectExactlyOnePrimaryAccount() throws Exception {
        UUID firstId = addAccount(FIRST_IBAN);
        UUID secondId = addAccount(SECOND_IBAN);

        mockMvc.perform(post("/users/me/bank-accounts/{accountId}/primary", secondId)
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(secondId.toString()))
                .andExpect(jsonPath("$.data.primary").value(true));

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                .andExpect(jsonPath("$.data[1].id").value(firstId.toString()))
                .andExpect(jsonPath("$.data[1].primary").value(false));
    }

    @Test
    void concurrentSelectionsBothCompleteAndLeaveOnePrimaryAccount() throws Exception {
        UUID firstId = addAccount(FIRST_IBAN);
        UUID secondId = addAccount(SECOND_IBAN);

        for (int attempt = 0; attempt < 10; attempt++) {
            selectPrimary(firstId);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Integer> first = executor.submit(() -> selectAtTheSameTime(firstId, ready, start));
                Future<Integer> second = executor.submit(() -> selectAtTheSameTime(secondId, ready, start));
                ready.await();
                start.countDown();

                assertEquals(200, first.get());
                assertEquals(200, second.get());
            }

            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from bank_accounts where user_id = ? and is_primary = true "
                            + "and is_active = true and is_deleted = false",
                    Integer.class, userId));
        }
    }

    @Test
    void deletingAnotherUsersAccountLooksExactlyLikeAMissingAccount() throws Exception {
        UUID otherAccountId = addAccount(otherUserId, SECOND_IBAN);

        mockMvc.perform(post("/users/me/bank-accounts/{accountId}/primary", otherAccountId)
                        .with(user(principal(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BANK_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(delete("/users/me/bank-accounts/{accountId}", otherAccountId)
                        .with(user(principal(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BANK_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(otherUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void theUserCanDeleteTheirOwnAccount() throws Exception {
        UUID accountId = addAccount(FIRST_IBAN);

        mockMvc.perform(delete("/users/me/bank-accounts/{accountId}", accountId)
                        .with(user(principal(userId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aForeignOrInvalidIbanIsRejectedWithoutEchoingTheRawValue() throws Exception {
        String foreignIban = "DE070006200119000006672315";

        mockMvc.perform(post("/users/me/bank-accounts")
                        .with(user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + foreignIban + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("iban"))
                .andExpect(content().string(not(containsString(foreignIban))));
    }

    @Test
    void malformedJsonNeverWritesTheRawIbanToTheExceptionHandlerLog() throws Exception {
        String rawIban = "TR180006200119000006672315";
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(post("/users/me/bank-accounts")
                            .with(user(principal(userId)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"iban\":\"broken\"}" + rawIban))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCategory").value("REQUEST_MALFORMED"))
                    .andExpect(content().string(not(containsString(rawIban))));

            String handlerLogs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertFalse(handlerLogs.contains(rawIban));
            assertTrue(handlerLogs.contains("REQUEST_MALFORMED"));
            assertTrue(handlerLogs.contains("correlationId="));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void addingTheSameAccountTwiceReturnsAConflictWithoutEchoingTheIban() throws Exception {
        addAccount(FIRST_IBAN);

        mockMvc.perform(post("/users/me/bank-accounts")
                        .with(user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + FIRST_IBAN + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BANK_ACCOUNT_ALREADY_EXISTS"))
                .andExpect(content().string(not(containsString("TR180006200119000006672315"))));
    }

    @Test
    void theLegacyProfileIbanFieldCanSelectAnExistingSecondaryAccount() throws Exception {
        UUID firstId = addAccount(FIRST_IBAN);
        UUID secondId = addAccount(SECOND_IBAN);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/users/me")
                        .with(user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + SECOND_IBAN + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                .andExpect(jsonPath("$.data[1].id").value(firstId.toString()))
                .andExpect(jsonPath("$.data[1].primary").value(false));
    }

    @Test
    void changingTheLegacyProfileIbanResetsFutureVerificationState() throws Exception {
        UUID accountId = addAccount(FIRST_IBAN);
        jdbcTemplate.update("update bank_accounts set verification_status = 'VERIFIED' where id = ?", accountId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/users/me")
                        .with(user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + SECOND_IBAN + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me/bank-accounts")
                        .with(user(principal(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$.data[0].maskedIban").value("TR96***5006"))
                .andExpect(jsonPath("$.data[0].verificationStatus").value("UNVERIFIED"));
    }

    private UUID addAccount(String iban) throws Exception {
        return addAccount(userId, iban);
    }

    private UUID addAccount(UUID ownerId, String iban) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/me/bank-accounts")
                        .with(user(principal(ownerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"" + iban + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("id").asString());
    }

    private void selectPrimary(UUID accountId) throws Exception {
        mockMvc.perform(post("/users/me/bank-accounts/{accountId}/primary", accountId)
                        .with(user(principal(userId))))
                .andExpect(status().isOk());
    }

    private int selectAtTheSameTime(UUID accountId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/users/me/bank-accounts/{accountId}/primary", accountId)
                        .with(user(principal(userId))))
                .andReturn().getResponse().getStatus();
    }

    private void insertUser(UUID id) {
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                id, email(id));
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }

    private String email(UUID id) {
        return id + "@example.com";
    }
}
