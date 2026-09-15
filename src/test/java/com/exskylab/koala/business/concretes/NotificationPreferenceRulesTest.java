package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.dtos.notificationPreference.request.NotificationPreferenceEntryDto;
import com.exskylab.koala.core.dtos.notificationPreference.request.UpdateNotificationPreferencesDto;
import com.exskylab.koala.core.dtos.notificationPreference.response.NotificationPreferenceDto;
import com.exskylab.koala.core.dtos.notificationPreference.response.UnsubscribedChannelDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.helpers.DeviceHints;
import com.exskylab.koala.core.mappers.NotificationPreferenceMapperImpl;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.NotificationPreferenceDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationPreference;
import com.exskylab.koala.entities.UnsubscribeToken;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationPreferenceRulesTest {

    private final UUID userId = UUID.randomUUID();

    private final ClientContext client = new ClientContext("203.0.113.7", "Koala IT", "device-1", ClientPlatform.UNKNOWN, DeviceHints.NONE);

    private NotificationPreferenceDao preferenceDao;
    private CommercialMessageConsentDao consentDao;
    private UnsubscribeTokenService unsubscribeTokenService;
    private SecurityService securityService;
    private IysService iysService;
    private NotificationPreferenceManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        preferenceDao = mock(NotificationPreferenceDao.class);
        when(preferenceDao.findByUserIdAndCategoryAndChannel(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(preferenceDao.findAllByUserId(any())).thenReturn(new ArrayList<>());
        when(preferenceDao.save(any())).thenAnswer(call -> call.getArgument(0));

        user = new User();
        user.setId(userId);
        user.setEmail("preference-rules@koala.test");
        user.setEmailVerified(true);

        securityService = mock(SecurityService.class);
        when(securityService.getAuthenticatedUser()).thenReturn(user);
        when(securityService.getAuthenticatedUserId()).thenReturn(userId);

        consentDao = mock(CommercialMessageConsentDao.class);
        unsubscribeTokenService = mock(UnsubscribeTokenService.class);
        iysService = mock(IysService.class);

        manager = new NotificationPreferenceManager(preferenceDao, consentDao, unsubscribeTokenService,
                securityService, iysService, new NotificationPreferenceMapperImpl());
    }

    private void storedRow(NotificationCategory category, NotificationChannel channel, boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setCategory(category);
        preference.setChannel(channel);
        preference.setEnabled(enabled);

        when(preferenceDao.findByUserIdAndCategoryAndChannel(eq(userId), eq(category), eq(channel)))
                .thenReturn(Optional.of(preference));
    }

    @Test
    void aTransactionalCategoryStaysOnEvenWhenAStoredRowSaysOtherwise() {
        storedRow(NotificationCategory.ACCOUNT_SECURITY, NotificationChannel.EMAIL, false);

        assertTrue(manager.isAllowed(userId, NotificationCategory.ACCOUNT_SECURITY, NotificationChannel.EMAIL));
    }

    @Test
    void anAlertCategoryIsOnUntilTheUserSaysOtherwise() {
        assertTrue(manager.isAllowed(userId, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));

        storedRow(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, false);

        assertFalse(manager.isAllowed(userId, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));
    }

    @Test
    void aCompanyContactInvitationSurvivesAStoredRowThatSwitchesCompanyUpdatesOff() {
        storedRow(NotificationCategory.COMPANY_UPDATES, NotificationChannel.EMAIL, false);

        assertTrue(manager.isAllowed(userId, NotificationCategory.COMPANY_UPDATES, NotificationChannel.EMAIL),
                "yetkili daveti e-postadan başka hiçbir kanaldan gitmiyor, tercih onu susturamamalı");
    }

    @Test
    void aCommercialCategoryIsOffUntilTheUserOptsIn() {
        assertFalse(manager.isAllowed(userId, NotificationCategory.MARKETINGS, NotificationChannel.EMAIL));

        storedRow(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL, true);

        assertTrue(manager.isAllowed(userId, NotificationCategory.MARKETINGS, NotificationChannel.EMAIL));
    }

    @Test
    void switchingOffOneChannelLeavesTheOtherChannelsOfTheSameCategoryAlone() {
        storedRow(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, false);

        assertFalse(manager.isAllowed(userId, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));
        assertTrue(manager.isAllowed(userId, NotificationCategory.JOB_ALERTS, NotificationChannel.PUSH));
        assertTrue(manager.isAllowed(userId, NotificationCategory.JOB_ALERTS, NotificationChannel.IN_APP));
    }

    @Test
    void anUnknownRecipientIsNeverSilenced() {
        assertTrue(manager.isAllowed(null, NotificationCategory.MARKETINGS, NotificationChannel.EMAIL));
    }

    @Test
    void turningOffAChannelIsStoredAndComesBackInTheResolvedView() {
        List<NotificationPreferenceDto> view = manager.updateMyPreferences(
                request(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, false), client);

        assertFalse(savedRow().isEnabled());
        assertEquals(NotificationCategory.JOB_ALERTS, savedRow().getCategory());
        assertEquals(NotificationChannel.EMAIL, savedRow().getChannel());
        assertEquals(user, savedRow().getUser());

        assertFalse(entryOf(view, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL).enabled());
        assertTrue(entryOf(view, NotificationCategory.JOB_ALERTS, NotificationChannel.PUSH).enabled());
    }

    @Test
    void theResolvedViewMarksTransactionalCategoriesAsOnAndNotEditable() {
        NotificationPreferenceDto transactional = entryOf(manager.getMyPreferences(),
                NotificationCategory.APPLICATION_UPDATES, NotificationChannel.EMAIL);

        assertTrue(transactional.enabled());
        assertFalse(transactional.editable());

        NotificationPreferenceDto optional = entryOf(manager.getMyPreferences(),
                NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL);

        assertTrue(optional.editable());
    }

    @Test
    void theResolvedViewOffersNoSwitchForCompanyUpdates() {
        NotificationPreferenceDto companyUpdates = entryOf(manager.getMyPreferences(),
                NotificationCategory.COMPANY_UPDATES, NotificationChannel.EMAIL);

        assertTrue(companyUpdates.enabled());
        assertFalse(companyUpdates.editable());
    }

    @Test
    void theResolvedViewOffersSmsAsAUserPreference() {
        assertTrue(manager.getMyPreferences().stream().anyMatch(entry -> entry.channel() == NotificationChannel.SMS));
    }

    @Test
    void aSecondDecisionOnTheSameChannelUpdatesTheExistingRowRatherThanAddingOne() {
        NotificationPreference stored = new NotificationPreference();
        stored.setId(UUID.randomUUID());
        stored.setUser(user);
        stored.setCategory(NotificationCategory.JOB_ALERTS);
        stored.setChannel(NotificationChannel.EMAIL);
        stored.setEnabled(false);
        when(preferenceDao.findAllByUserId(userId)).thenReturn(new ArrayList<>(List.of(stored)));

        manager.updateMyPreferences(
                request(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, true), client);

        assertEquals(stored.getId(), savedRow().getId());
        assertTrue(savedRow().isEnabled());
    }

    @Test
    void optingIntoACommercialMessageRecordsTheMomentTheChannelAndTheIpAddress() {
        manager.updateMyPreferences(
                request(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL, true), client);

        CommercialMessageConsent consent = recordedConsent();

        assertTrue(consent.isGranted());
        assertEquals(NotificationChannel.EMAIL, consent.getChannel());
        assertEquals("203.0.113.7", consent.getIpAddress());
        assertEquals("Koala IT", consent.getUserAgent());
        assertEquals(user, consent.getUser());
        assertNotNull(consent.getRecordedAt());
        assertEquals(CommercialConsentSource.PREFERENCE_CENTRE, consent.getSource());
    }

    @Test
    void withdrawingACommercialMessageConsentIsRecordedJustAsCarefullyAsGivingIt() {
        manager.updateMyPreferences(
                request(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL, false), client);

        assertFalse(recordedConsent().isGranted());
        assertNotNull(recordedConsent().getRecordedAt());
    }

    @Test
    void aNonCommercialCategoryLeavesNoConsentRecordBehind() {
        manager.updateMyPreferences(
                request(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, false), client);

        verifyNoInteractions(consentDao);
    }

    @Test
    void anUnsubscribeLinkSwitchesOffOnlyTheChannelItNames() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));

        manager.unsubscribe("gecerli-token", client);

        assertEquals(NotificationCategory.JOB_ALERTS, savedRow().getCategory());
        assertEquals(NotificationChannel.EMAIL, savedRow().getChannel());
        assertFalse(savedRow().isEnabled());
        assertEquals(user, savedRow().getUser());
    }

    @Test
    void anUnsubscribeLinkNeedsNoLoggedInUser() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));

        manager.unsubscribe("gecerli-token", client);

        verifyNoInteractions(securityService);
    }

    @Test
    void unsubscribingFromACommercialMessageIsRecordedAsAWithdrawnConsent() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL));
        optedIn(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL);

        manager.unsubscribe("gecerli-token", client);

        assertFalse(recordedConsent().isGranted());
        assertEquals(NotificationChannel.EMAIL, recordedConsent().getChannel());
        assertEquals("203.0.113.7", recordedConsent().getIpAddress());
        assertEquals(CommercialConsentSource.UNSUBSCRIBE_LINK, recordedConsent().getSource());
    }

    @Test
    void theUnsubscribeAnswerNamesTheCategoryAndChannelThatWereSwitchedOff() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL));
        optedIn(NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL);

        UnsubscribedChannelDto answer = manager.unsubscribe("gecerli-token", client);

        assertEquals(NotificationCategory.NEWSLETTER, answer.category());
        assertEquals(NotificationChannel.EMAIL, answer.channel());
        assertFalse(answer.alreadyDisabled());
    }

    @Test
    void aSecondClickOnTheSameLinkAnswersTheSameChannelWithoutRewritingAnything() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL));
        optedIn(NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL);

        UnsubscribedChannelDto first = manager.unsubscribe("gecerli-token", client);
        UnsubscribedChannelDto second = manager.unsubscribe("gecerli-token", client);

        assertEquals(first.category(), second.category());
        assertEquals(first.channel(), second.channel());
        assertFalse(first.alreadyDisabled());
        assertTrue(second.alreadyDisabled());
        verify(preferenceDao, org.mockito.Mockito.times(1)).save(any());
        verify(consentDao, org.mockito.Mockito.times(1)).save(any());
        verify(iysService, org.mockito.Mockito.times(1)).enqueue(any(), any(), any(), eq(false), any());
    }

    @Test
    void aLinkForAChannelThatWasNeverOpenedInLeavesNoRowAndNoConsentBehind() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL));

        UnsubscribedChannelDto answer = manager.unsubscribe("gecerli-token", client);

        assertTrue(answer.alreadyDisabled());
        assertEquals(NotificationCategory.MARKETINGS, answer.category());
        verify(preferenceDao, never()).save(any());
        verifyNoInteractions(consentDao, iysService);
    }

    @Test
    void anAlertChannelThatIsOnByDefaultIsSwitchedOffByItsLinkAndStaysOffOnTheSecondClick() {
        when(unsubscribeTokenService.consume("gecerli-token"))
                .thenReturn(token(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL));
        List<NotificationPreference> rows = new ArrayList<>();
        when(preferenceDao.findAllByUserId(userId)).thenReturn(rows);
        when(preferenceDao.save(any())).thenAnswer(call -> {
            NotificationPreference row = call.getArgument(0);
            if (!rows.contains(row)) {
                rows.add(row);
            }
            return row;
        });

        UnsubscribedChannelDto first = manager.unsubscribe("gecerli-token", client);
        UnsubscribedChannelDto second = manager.unsubscribe("gecerli-token", client);

        assertFalse(first.alreadyDisabled());
        assertTrue(second.alreadyDisabled());
        assertEquals(1, rows.size());
        assertFalse(rows.getFirst().isEnabled());
        verify(preferenceDao, org.mockito.Mockito.times(1)).save(any());
    }

    private void optedIn(NotificationCategory category, NotificationChannel channel) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(user);
        preference.setCategory(category);
        preference.setChannel(channel);
        preference.setEnabled(true);
        List<NotificationPreference> rows = new ArrayList<>();
        rows.add(preference);
        when(preferenceDao.findAllByUserId(userId)).thenReturn(rows);
    }

    private UnsubscribeToken token(NotificationCategory category, NotificationChannel channel) {
        UnsubscribeToken token = new UnsubscribeToken();
        token.setUser(user);
        token.setCategory(category);
        token.setChannel(channel);
        return token;
    }

    private CommercialMessageConsent recordedConsent() {
        ArgumentCaptor<CommercialMessageConsent> captor = ArgumentCaptor.forClass(CommercialMessageConsent.class);
        verify(consentDao).save(captor.capture());
        return captor.getValue();
    }

    private UpdateNotificationPreferencesDto request(NotificationCategory category, NotificationChannel channel,
                                               boolean enabled) {
        UpdateNotificationPreferencesDto request = new UpdateNotificationPreferencesDto();
        request.setPreferences(List.of(new NotificationPreferenceEntryDto(category, channel, enabled)));
        return request;
    }

    private NotificationPreference savedRow() {
        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceDao).save(captor.capture());
        return captor.getValue();
    }

    private NotificationPreferenceDto entryOf(List<NotificationPreferenceDto> view, NotificationCategory category,
                                              NotificationChannel channel) {
        return view.stream()
                .filter(entry -> entry.category() == category && entry.channel() == channel)
                .findFirst()
                .orElseThrow();
    }
}
