package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PasswordResetTokenService;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.security.PasswordResetTokenGenerator;
import com.exskylab.koala.core.security.TokenDigest;
import com.exskylab.koala.dataAccess.PasswordResetTokenDao;
import com.exskylab.koala.entities.PasswordResetToken;
import com.exskylab.koala.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordResetTokenManager implements PasswordResetTokenService {

    private final PasswordResetTokenDao passwordResetTokenDao;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final long tokenValidityMs;

    public PasswordResetTokenManager(PasswordResetTokenDao passwordResetTokenDao,
                                     PasswordResetTokenGenerator tokenGenerator,
                                     @Value("${password-reset.token-validity-ms}") long tokenValidityMs) {
        this.passwordResetTokenDao = passwordResetTokenDao;
        this.tokenGenerator = tokenGenerator;
        this.tokenValidityMs = tokenValidityMs;
    }

    @Override
    @Transactional
    public String issue(User user) {
        Instant now = Instant.now();
        passwordResetTokenDao.revokeActiveForUser(user.getId(), now);

        String rawToken = tokenGenerator.generate();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenDigest(TokenDigest.of(rawToken));
        token.setExpiresAt(now.plusMillis(tokenValidityMs));
        passwordResetTokenDao.save(token);
        return rawToken;
    }

    @Override
    @Transactional
    public UUID consume(String rawToken) {
        String tokenDigest = TokenDigest.of(rawToken);
        UUID userId = passwordResetTokenDao.findUserIdByTokenDigest(tokenDigest)
                .orElseThrow(this::invalidToken);

        if (passwordResetTokenDao.consume(tokenDigest, Instant.now()) != 1) {
            throw invalidToken();
        }
        return userId;
    }

    @Override
    @Transactional
    public void revokeActiveForUser(UUID userId) {
        passwordResetTokenDao.revokeActiveForUser(userId, Instant.now());
    }

    private ValidationException invalidToken() {
        return new ValidationException(AuthMessages.PASSWORD_RESET_TOKEN_INVALID);
    }
}
