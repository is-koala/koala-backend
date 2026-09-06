package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysReconciliationService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.IysConsentTaskDao;
import com.exskylab.koala.dataAccess.IysPermissionStateDao;
import com.exskylab.koala.dataAccess.NotificationPreferenceDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationPreference;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.IysFailureCode;
import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Transactional
@TestPropertySource(properties = {
        "iys.enabled=true",
        "iys.api-key=test-key",
        "iys.api-hash=test-hash",
        "iys.brand-code=test-brand"
})
class IysReconciliationIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-09-06T17:45:00Z");

    @Autowired
    private IysReconciliationService reconciliationService;

    @Autowired
    private IysService iysService;

    @Autowired
    private NotificationPreferenceDao notificationPreferenceDao;

    @Autowired
    private CommercialMessageConsentDao consentDao;

    @Autowired
    private IysConsentTaskDao taskDao;

    @Autowired
    private IysPermissionStateDao permissionStateDao;

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
    void anIysWithdrawalDisablesEveryLocalCommercialCategoryWithoutEchoingAnOutboxTask() {
        User user = userDao.save(user());
        notificationPreferenceDao.save(preference(user, NotificationCategory.MARKETINGS, true));
        notificationPreferenceDao.save(preference(user, NotificationCategory.NEWSLETTER, true));
        when(providerClient.lookupConsent(user.getEmail(), NotificationChannel.EMAIL))
                .thenReturn(new IysPermissionResult(user.getEmail(), NotificationChannel.EMAIL,
                        false, WITHDRAWN_AT, "HS_WEB"));

        reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL);
        reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL);

        List<NotificationPreference> preferences = notificationPreferenceDao.findAllByUserId(user.getId());
        assertEquals(2, preferences.size());
        assertFalse(preferences.get(0).isEnabled());
        assertFalse(preferences.get(1).isEnabled());
        assertEquals(2, consentDao.findAllByUserIdOrderByRecordedAtDesc(user.getId()).size());
        assertEquals(CommercialConsentSource.IYS_RECONCILIATION,
                consentDao.findAllByUserIdOrderByRecordedAtDesc(user.getId()).getFirst().getSource());
        assertEquals(0, taskDao.findAll().stream()
                .filter(task -> task.getUser().getId().equals(user.getId())).count());
    }

    @Test
    void aFreshIysApprovalPermitsTheChannelWithoutOverridingLocalPreferences() {
        User user = userDao.save(user());
        NotificationPreference localOptIn = preference(user, NotificationCategory.NEWSLETTER, true);
        notificationPreferenceDao.save(localOptIn);
        when(providerClient.lookupConsent(user.getEmail(), NotificationChannel.EMAIL))
                .thenReturn(new IysPermissionResult(user.getEmail(), NotificationChannel.EMAIL,
                        true, WITHDRAWN_AT, "HS_WEB"));

        reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL);

        assertTrue(iysService.isAllowed(user, NotificationChannel.EMAIL, user.getEmail()));
        assertTrue(notificationPreferenceDao.findById(localOptIn.getId()).orElseThrow().isEnabled());
        assertEquals(0, consentDao.findAllByUserIdOrderByRecordedAtDesc(user.getId()).size());

        when(clock.instant()).thenReturn(NOW.plusSeconds(3601));
        assertFalse(iysService.isAllowed(user, NotificationChannel.EMAIL, user.getEmail()));
    }

    @Test
    void anOlderRegistryWithdrawalCannotOverrideANewerLocalApproval() {
        User user = userDao.save(user());
        NotificationPreference localOptIn = preference(user, NotificationCategory.NEWSLETTER, true);
        notificationPreferenceDao.save(localOptIn);
        CommercialMessageConsent consent = new CommercialMessageConsent();
        consent.setUser(user);
        consent.setCategory(NotificationCategory.NEWSLETTER);
        consent.setChannel(NotificationChannel.EMAIL);
        consent.setGranted(true);
        consent.setRecordedAt(NOW);
        consent.setSource(CommercialConsentSource.PREFERENCE_CENTRE);
        consentDao.save(consent);
        iysService.enqueue(user, consent, NotificationChannel.EMAIL, true, null);
        when(providerClient.lookupConsent(user.getEmail(), NotificationChannel.EMAIL))
                .thenReturn(new IysPermissionResult(user.getEmail(), NotificationChannel.EMAIL,
                        false, WITHDRAWN_AT, "HS_WEB"));

        reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL);

        assertTrue(notificationPreferenceDao.findById(localOptIn.getId()).orElseThrow().isEnabled());
        assertTrue(permissionStateDao.findByUserIdAndChannel(user.getId(), NotificationChannel.EMAIL).isEmpty());
    }

    @Test
    void aFailedReconciliationPersistsBackoffSoItCannotStarveTheNextBatch() {
        User user = userDao.save(user());
        when(providerClient.lookupConsent(user.getEmail(), NotificationChannel.EMAIL))
                .thenReturn(new IysPermissionResult(user.getEmail(), NotificationChannel.EMAIL,
                        true, WITHDRAWN_AT, "HS_WEB"));
        reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL);

        Instant failedAt = NOW.plusSeconds(3601);
        when(clock.instant()).thenReturn(failedAt);
        doThrow(new ExternalServiceException(IysMessages.PROVIDER_UNAVAILABLE))
                .when(providerClient).lookupConsent(user.getEmail(), NotificationChannel.EMAIL);

        assertThrows(ExternalServiceException.class,
                () -> reconciliationService.reconcile(user.getId(), NotificationChannel.EMAIL));

        var state = permissionStateDao.findByUserIdAndChannel(user.getId(), NotificationChannel.EMAIL).orElseThrow();
        assertEquals(1, state.getReconciliationAttempts());
        assertEquals(IysFailureCode.PROVIDER_UNAVAILABLE, state.getLastFailureCode());
        assertEquals(failedAt.plusSeconds(60), state.getNextReconciliationAt());
        assertFalse(iysService.findStalePermissions().stream()
                .anyMatch(target -> target.userId().equals(user.getId())));
    }

    private User user() {
        User user = new User();
        user.setEmail("iys-reconciliation@koala.test");
        return user;
    }

    private NotificationPreference preference(User user, NotificationCategory category, boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(user);
        preference.setCategory(category);
        preference.setChannel(NotificationChannel.EMAIL);
        preference.setEnabled(enabled);
        return preference;
    }
}
