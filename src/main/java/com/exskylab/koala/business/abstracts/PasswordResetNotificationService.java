package com.exskylab.koala.business.abstracts;

import java.util.UUID;

public interface PasswordResetNotificationService {

    void sendCompletionEmail(UUID userId);
}
