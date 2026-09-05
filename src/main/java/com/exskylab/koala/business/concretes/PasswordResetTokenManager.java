package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PasswordResetTokenService;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.security.PasswordResetTokenGenerator;
import com.exskylab.koala.dataAccess.PasswordResetTokenDao;
import com.exskylab.koala.entities.PasswordResetToken;
import com.exskylab.koala.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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
        token.setTokenDigest(digest(rawToken));
        token.setExpiresAt(now.plusMillis(tokenValidityMs));
        passwordResetTokenDao.save(token);
        return rawToken;
    }

    @Override
    @Transactional
    public UUID consume(String rawToken) {
        String tokenDigest = digest(rawToken);
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

    private String digest(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ValidationException invalidToken() {
        return new ValidationException(AuthMessages.PASSWORD_RESET_TOKEN_INVALID);
    }
}
