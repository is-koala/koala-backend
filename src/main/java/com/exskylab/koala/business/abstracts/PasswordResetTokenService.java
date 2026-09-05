package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.User;

import java.util.UUID;

public interface PasswordResetTokenService {

    String issue(User user);

    UUID consume(String rawToken);

    void revokeActiveForUser(UUID userId);
}
