package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.utilities.iys.IysConsentRequest;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.IysConsentTaskDao;
import com.exskylab.koala.dataAccess.IysPermissionStateDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.IysPermissionStatus;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "iys.enabled=true",
        "iys.api-key=test-key",
        "iys.api-hash=test-hash",
        "iys.brand-code=test-brand"
})
class IysTaskConcurrencyIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");

    @Autowired private IysService iysService;
    @Autowired private IysConsentTaskDao taskDao;
    @Autowired private IysPermissionStateDao permissionStateDao;
    @Autowired private CommercialMessageConsentDao consentDao;
    @Autowired private UserDao userDao;
    @MockitoBean private IysProviderClient providerClient;
    @MockitoBean private Clock clock;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final List<UUID> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void cleanCommittedFixtures() {
        for (UUID userId : userIds) {
            jdbcTemplate.update("delete from iys_consent_tasks where user_id = ?", userId);
            jdbcTemplate.update("delete from iys_permission_states where user_id = ?", userId);
            jdbcTemplate.update("delete from commercial_message_consents where user_id = ?", userId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void twoWorkersCannotDispatchTheSameTask() throws Exception {
        IysConsentTask task = pendingTask(savedUser("iys-single-claim@koala.test"), true, NOW);
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        blockRegistration(enteredProvider, releaseProvider);
        when(providerClient.lookupConsent(task.getRecipient(), NotificationChannel.EMAIL))
                .thenReturn(permission(task.getRecipient(), true));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> iysService.runTask(task.getId()));
            assertTrue(enteredProvider.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> iysService.runTask(task.getId()));
            assertFalse(second.get(5, TimeUnit.SECONDS));
            releaseProvider.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        }

        assertEquals(IysConsentTaskStatus.COMPLETED, taskDao.findById(task.getId()).orElseThrow().getStatus());
        verify(providerClient, times(1)).registerConsent(any(IysConsentRequest.class));
    }

    @Test
    void aDecisionArrivingDuringDispatchWinsAfterTheActiveTaskReleasesItsClaim() throws Exception {
        User user = savedUser("iys-last-decision@koala.test");
        IysConsentTask grant = pendingTask(user, true, NOW);
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        blockRegistration(enteredProvider, releaseProvider);
        when(providerClient.lookupConsent(user.getEmail(), NotificationChannel.EMAIL))
                .thenReturn(permission(user.getEmail(), true), permission(user.getEmail(), false));

        IysConsentTask withdrawal;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var activeGrant = executor.submit(() -> iysService.runTask(grant.getId()));
            assertTrue(enteredProvider.await(5, TimeUnit.SECONDS));
            withdrawal = pendingTask(user, false, NOW);
            assertFalse(iysService.runTask(withdrawal.getId()));
            releaseProvider.countDown();
            assertFalse(activeGrant.get(5, TimeUnit.SECONDS));
        }

        assertEquals(IysConsentTaskStatus.SUPERSEDED,
                taskDao.findById(grant.getId()).orElseThrow().getStatus());
        assertTrue(iysService.runTask(withdrawal.getId()));
        assertEquals(IysPermissionStatus.REJECTED,
                permissionStateDao.findByUserIdAndChannel(user.getId(), NotificationChannel.EMAIL)
                        .orElseThrow().getStatus());
        verify(providerClient, times(2)).registerConsent(any(IysConsentRequest.class));
    }

    private void blockRegistration(CountDownLatch entered, CountDownLatch release) {
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("provider release timed out");
            }
            return null;
        }).when(providerClient).registerConsent(any());
    }

    private IysPermissionResult permission(String recipient, boolean granted) {
        return new IysPermissionResult(recipient, NotificationChannel.EMAIL, granted, NOW, "HS_WEB");
    }

    private User savedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(true);
        User saved = userDao.saveAndFlush(user);
        userIds.add(saved.getId());
        return saved;
    }

    private IysConsentTask pendingTask(User user, boolean granted, Instant recordedAt) {
        CommercialMessageConsent consent = new CommercialMessageConsent();
        consent.setUser(user);
        consent.setCategory(NotificationCategory.MARKETINGS);
        consent.setChannel(NotificationChannel.EMAIL);
        consent.setGranted(granted);
        consent.setRecordedAt(recordedAt);
        consent.setSource(CommercialConsentSource.PREFERENCE_CENTRE);
        consentDao.saveAndFlush(consent);
        iysService.enqueue(user, consent, NotificationChannel.EMAIL, granted, null);
        return taskDao.findAll().stream().filter(task -> task.getConsent().getId().equals(consent.getId()))
                .findFirst().orElseThrow();
    }
}
