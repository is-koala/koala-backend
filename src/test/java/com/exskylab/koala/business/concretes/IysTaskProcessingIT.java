package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.IysConsentTaskDao;
import com.exskylab.koala.dataAccess.IysPermissionStateDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.IysFailureCode;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Transactional
@TestPropertySource(properties = {
        "iys.enabled=true",
        "iys.retry.initial-backoff=PT1M",
        "iys.retry.max-backoff=PT1H",
        "iys.retry.max-attempts=3",
        "iys.api-key=test-key",
        "iys.api-hash=test-hash",
        "iys.brand-code=test-brand"
})
class IysTaskProcessingIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");

    @Autowired
    private IysService iysService;

    @Autowired
    private IysConsentTaskDao iysConsentTaskDao;

    @Autowired
    private IysPermissionStateDao iysPermissionStateDao;

    @Autowired
    private CommercialMessageConsentDao commercialMessageConsentDao;

    @Autowired
    private UserDao userDao;

    @MockitoBean
    private IysProviderClient providerClient;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void aProviderFailureSchedulesTheTaskWithExponentialBackoff() {
        IysConsentTask task = pendingTask();
        doThrow(new ExternalServiceException(IysMessages.PROVIDER_UNAVAILABLE))
                .when(providerClient).registerConsent(any());

        assertFalse(iysService.runTask(task.getId()));

        IysConsentTask retried = iysConsentTaskDao.findById(task.getId()).orElseThrow();
        assertEquals(IysConsentTaskStatus.PENDING, retried.getStatus());
        assertEquals(1, retried.getAttempts());
        assertEquals(NOW.plusSeconds(60), retried.getNextAttemptAt());
        assertEquals(IysFailureCode.PROVIDER_UNAVAILABLE, retried.getLastFailureCode());
        assertNotNull(retried.getUpdatedAt());
        assertFalse(iysService.findDueTaskIds().contains(task.getId()));

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        assertEquals(java.util.List.of(task.getId()), iysService.findDueTaskIds());

        assertFalse(iysService.runTask(task.getId()));
        IysConsentTask secondRetry = iysConsentTaskDao.findById(task.getId()).orElseThrow();
        assertEquals(2, secondRetry.getAttempts());
        assertEquals(NOW.plusSeconds(180), secondRetry.getNextAttemptAt());

        when(clock.instant()).thenReturn(NOW.plusSeconds(180));
        assertFalse(iysService.runTask(task.getId()));
        IysConsentTask exhausted = iysConsentTaskDao.findById(task.getId()).orElseThrow();
        assertEquals(IysConsentTaskStatus.EXHAUSTED, exhausted.getStatus());
        assertEquals(3, exhausted.getAttempts());
        assertFalse(iysService.findDueTaskIds().contains(task.getId()));
    }

    @Test
    void aTaskCompletesOnlyAfterTheRegistryReflectsTheDecision() {
        IysConsentTask task = pendingTask();
        when(providerClient.lookupConsent(task.getRecipient(), task.getChannel()))
                .thenReturn(new IysPermissionResult(task.getRecipient(), task.getChannel(),
                        true, NOW, "HS_WEB"));

        assertTrue(iysService.runTask(task.getId()));

        IysConsentTask completed = iysConsentTaskDao.findById(task.getId()).orElseThrow();
        assertEquals(IysConsentTaskStatus.COMPLETED, completed.getStatus());
        assertEquals(NOW, completed.getCompletedAt());
        assertTrue(iysService.isAllowed(completed.getUser(), NotificationChannel.EMAIL, completed.getRecipient()));
    }

    @Test
    void aConsentForThePreviousEmailNeverAuthorizesTheUsersNewEmail() {
        User user = savedUser("old-recipient@koala.test");
        IysConsentTask task = pendingTask(user, true, NOW);
        user.setEmail("new-recipient@koala.test");
        userDao.saveAndFlush(user);
        when(providerClient.lookupConsent(task.getRecipient(), task.getChannel()))
                .thenReturn(new IysPermissionResult(task.getRecipient(), task.getChannel(),
                        true, NOW, "HS_WEB"));

        assertTrue(iysService.runTask(task.getId()));

        var permission = iysPermissionStateDao
                .findByUserIdAndChannel(user.getId(), NotificationChannel.EMAIL).orElseThrow();
        assertEquals("old-recipient@koala.test", permission.getRecipient());
        assertFalse(iysService.isAllowed(user, NotificationChannel.EMAIL, user.getEmail()));
    }

    @Test
    void anOlderRetryIsSupersededByANewerDecisionForTheSameChannel() {
        User user = savedUser("iys-ordering@koala.test");
        IysConsentTask olderGrant = pendingTask(user, true, NOW);
        pendingTask(user, false, NOW.plusSeconds(1));

        assertFalse(iysService.runTask(olderGrant.getId()));

        IysConsentTask superseded = iysConsentTaskDao.findById(olderGrant.getId()).orElseThrow();
        assertEquals(IysConsentTaskStatus.SUPERSEDED, superseded.getStatus());
        assertEquals(NOW, superseded.getCompletedAt());
        assertEquals(0, superseded.getAttempts());
        verifyNoInteractions(providerClient);
    }

    @Test
    void anAcceptedRegistrationIsPolledWithoutRegisteringTheDecisionAgain() {
        IysConsentTask task = pendingTask();
        when(providerClient.lookupConsent(task.getRecipient(), task.getChannel()))
                .thenReturn(new IysPermissionResult(task.getRecipient(), task.getChannel(),
                                false, NOW.minusSeconds(60), "HS_WEB"),
                        new IysPermissionResult(task.getRecipient(), task.getChannel(),
                                true, NOW, "HS_WEB"));

        assertFalse(iysService.runTask(task.getId()));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        assertTrue(iysService.runTask(task.getId()));

        verify(providerClient, times(1)).registerConsent(any());
        assertEquals(IysConsentTaskStatus.COMPLETED,
                iysConsentTaskDao.findById(task.getId()).orElseThrow().getStatus());
    }

    private IysConsentTask pendingTask() {
        return pendingTask(savedUser("iys-retry@koala.test"), true, NOW);
    }

    private User savedUser(String email) {
        User user = new User();
        user.setEmail(email);
        return userDao.save(user);
    }

    private IysConsentTask pendingTask(User user, boolean granted, Instant recordedAt) {
        CommercialMessageConsent consent = new CommercialMessageConsent();
        consent.setUser(user);
        consent.setCategory(NotificationCategory.MARKETINGS);
        consent.setChannel(NotificationChannel.EMAIL);
        consent.setGranted(granted);
        consent.setRecordedAt(recordedAt);
        consent.setSource(CommercialConsentSource.PREFERENCE_CENTRE);
        commercialMessageConsentDao.save(consent);

        iysService.enqueue(user, consent, NotificationChannel.EMAIL, granted, null);
        iysConsentTaskDao.flush();
        return iysConsentTaskDao.findAll().stream()
                .filter(task -> task.getConsent().equals(consent))
                .findFirst()
                .orElseThrow();
    }
}
