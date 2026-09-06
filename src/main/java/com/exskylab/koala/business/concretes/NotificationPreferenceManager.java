package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.dtos.notificationPreference.request.NotificationPreferenceEntryDto;
import com.exskylab.koala.core.dtos.notificationPreference.request.UpdateNotificationPreferencesDto;
import com.exskylab.koala.core.dtos.notificationPreference.response.NotificationPreferenceDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.NotificationPreferenceDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationPreference;
import com.exskylab.koala.entities.UnsubscribeToken;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationPreferenceManager implements NotificationPreferenceService {

    private static final int USER_AGENT_LIMIT = 512;

    private final NotificationPreferenceDao notificationPreferenceDao;
    private final CommercialMessageConsentDao commercialMessageConsentDao;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final SecurityService securityService;
    private final IysService iysService;

    public NotificationPreferenceManager(NotificationPreferenceDao notificationPreferenceDao,
                                         CommercialMessageConsentDao commercialMessageConsentDao,
                                         UnsubscribeTokenService unsubscribeTokenService,
                                         SecurityService securityService,
                                         IysService iysService) {
        this.notificationPreferenceDao = notificationPreferenceDao;
        this.commercialMessageConsentDao = commercialMessageConsentDao;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.securityService = securityService;
        this.iysService = iysService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAllowed(UUID recipientId, NotificationCategory category, NotificationChannel channel) {
        if (recipientId == null || category == null || channel == null) {
            return true;
        }
        return enabledFor(category, notificationPreferenceDao
                .findByUserIdAndCategoryAndChannel(recipientId, category, channel).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> getMyPreferences() {
        return resolve(storedRowsOf(securityService.getAuthenticatedUserId()));
    }

    @Override
    @Transactional
    public List<NotificationPreferenceDto> updateMyPreferences(UpdateNotificationPreferencesDto request,
                                                               ClientContext client) {
        User owner = securityService.getAuthenticatedUser();
        Map<String, NotificationPreference> stored = storedRowsOf(owner.getId());

        for (NotificationPreferenceEntryDto entry : request.getPreferences()) {
            apply(owner, entry.getCategory(), entry.getChannel(), entry.getEnabled(), stored);
            recordCommercialDecision(owner, entry.getCategory(), entry.getChannel(), entry.getEnabled(),
                    CommercialConsentSource.PREFERENCE_CENTRE, client, stored);
        }

        log.info("Notification preferences updated for user {}: {} decisions",
                owner.getId(), request.getPreferences().size());
        return resolve(stored);
    }

    @Override
    @Transactional
    public void unsubscribe(String rawToken, ClientContext client) {
        UnsubscribeToken token = unsubscribeTokenService.consume(rawToken);
        User owner = token.getUser();

        Map<String, NotificationPreference> stored = storedRowsOf(owner.getId());
        apply(owner, token.getCategory(), token.getChannel(), false, stored);
        recordCommercialDecision(owner, token.getCategory(), token.getChannel(), false,
                CommercialConsentSource.UNSUBSCRIBE_LINK, client, stored);

        log.info("Unsubscribe link switched {} off on {} for user {}",
                token.getCategory(), token.getChannel(), owner.getId());
    }

    @Override
    @Transactional
    public void applyIysWithdrawal(User owner, NotificationChannel channel, Instant recordedAt) {
        Map<String, NotificationPreference> stored = storedRowsOf(owner.getId());
        for (NotificationCategory category : NotificationCategory.values()) {
            if (!category.getKind().isCommercial()) {
                continue;
            }
            apply(owner, category, channel, false, stored);
            CommercialMessageConsent consent = consent(owner, category, channel, false,
                    CommercialConsentSource.IYS_RECONCILIATION, recordedAt, null);
            commercialMessageConsentDao.save(consent);
        }
        log.info("IYS withdrawal applied to commercial preferences for user {} on {}", owner.getId(), channel);
    }

    private void apply(User owner, NotificationCategory category, NotificationChannel channel, boolean enabled,
                       Map<String, NotificationPreference> stored) {
        NotificationPreference preference = stored.get(keyOf(category, channel));

        if (preference == null) {
            preference = new NotificationPreference();
            preference.setUser(owner);
            preference.setCategory(category);
            preference.setChannel(channel);
            stored.put(keyOf(category, channel), preference);
        }

        preference.setEnabled(enabled);
        notificationPreferenceDao.save(preference);
    }

    private void recordCommercialDecision(User owner, NotificationCategory category, NotificationChannel channel,
                                          boolean granted, CommercialConsentSource source, ClientContext client,
                                          Map<String, NotificationPreference> stored) {
        if (!category.getKind().isCommercial()) {
            return;
        }

        CommercialMessageConsent consent = consent(owner, category, channel, granted, source, Instant.now(), client);

        commercialMessageConsentDao.save(consent);
        iysService.enqueue(owner, consent, channel, hasCommercialOptIn(channel, stored));
        log.info("Commercial message consent {} recorded for user {} on {}", granted, owner.getId(), channel);
    }

    private CommercialMessageConsent consent(User owner, NotificationCategory category, NotificationChannel channel,
                                               boolean granted, CommercialConsentSource source, Instant recordedAt,
                                               ClientContext client) {
        CommercialMessageConsent consent = new CommercialMessageConsent();
        consent.setUser(owner);
        consent.setCategory(category);
        consent.setChannel(channel);
        consent.setGranted(granted);
        consent.setRecordedAt(recordedAt);
        consent.setIpAddress(client == null ? null : client.ipAddress());
        consent.setUserAgent(truncate(client == null ? null : client.userAgent()));
        consent.setSource(source);
        return consent;
    }

    private boolean hasCommercialOptIn(NotificationChannel channel, Map<String, NotificationPreference> stored) {
        for (NotificationCategory category : NotificationCategory.values()) {
            if (category.getKind().isCommercial() && enabledFor(category, stored.get(keyOf(category, channel)))) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_LIMIT) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_LIMIT);
    }

    private Map<String, NotificationPreference> storedRowsOf(UUID ownerId) {
        Map<String, NotificationPreference> stored = new LinkedHashMap<>();
        for (NotificationPreference preference : notificationPreferenceDao.findAllByUserId(ownerId)) {
            stored.put(keyOf(preference.getCategory(), preference.getChannel()), preference);
        }
        return stored;
    }

    private List<NotificationPreferenceDto> resolve(Map<String, NotificationPreference> stored) {
        List<NotificationPreferenceDto> resolved = new ArrayList<>();

        for (NotificationCategory category : NotificationCategory.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                if (!channel.isPreferable()) {
                    continue;
                }
                resolved.add(new NotificationPreferenceDto(category, category.getKind(), channel,
                        enabledFor(category, stored.get(keyOf(category, channel))), category.isOptional()));
            }
        }
        return resolved;
    }

    private boolean enabledFor(NotificationCategory category, NotificationPreference stored) {
        if (!category.isOptional()) {
            return true;
        }
        return stored == null ? category.getKind().isEnabledByDefault() : stored.isEnabled();
    }

    private String keyOf(NotificationCategory category, NotificationChannel channel) {
        return category.name() + "/" + channel.name();
    }
}
