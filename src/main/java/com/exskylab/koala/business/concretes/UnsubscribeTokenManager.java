package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.constants.NotificationPreferenceMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.security.PasswordResetTokenGenerator;
import com.exskylab.koala.core.security.TokenDigest;
import com.exskylab.koala.dataAccess.UnsubscribeTokenDao;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.UnsubscribeToken;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class UnsubscribeTokenManager implements UnsubscribeTokenService {

    private final UnsubscribeTokenDao unsubscribeTokenDao;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final AuthLinkService authLinkService;
    private final long tokenValidityMs;

    public UnsubscribeTokenManager(UnsubscribeTokenDao unsubscribeTokenDao,
                                   PasswordResetTokenGenerator tokenGenerator,
                                   AuthLinkService authLinkService,
                                   @Value("${notification-preferences.unsubscribe-token-validity-ms}")
                                   long tokenValidityMs) {
        this.unsubscribeTokenDao = unsubscribeTokenDao;
        this.tokenGenerator = tokenGenerator;
        this.authLinkService = authLinkService;
        this.tokenValidityMs = tokenValidityMs;
    }

    @Override
    @Transactional
    public String issueLink(User recipient, NotificationCategory category, NotificationChannel channel) {
        String rawToken = tokenGenerator.generate();

        UnsubscribeToken token = new UnsubscribeToken();
        token.setUser(recipient);
        token.setCategory(category);
        token.setChannel(channel);
        token.setTokenDigest(TokenDigest.of(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(tokenValidityMs));
        unsubscribeTokenDao.save(token);

        return authLinkService.unsubscribeLandingLink(rawToken);
    }

    @Override
    @Transactional
    public UnsubscribeToken consume(String rawToken) {
        UnsubscribeToken token = unsubscribeTokenDao.findByTokenDigest(TokenDigest.of(rawToken))
                .orElseThrow(this::invalidToken);

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw invalidToken();
        }

        token.setLastUsedAt(Instant.now());
        return token;
    }

    private ValidationException invalidToken() {
        return new ValidationException(NotificationPreferenceMessages.UNSUBSCRIBE_TOKEN_INVALID);
    }
}
