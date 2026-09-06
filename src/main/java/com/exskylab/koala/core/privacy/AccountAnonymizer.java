package com.exskylab.koala.core.privacy;

import java.util.UUID;

public interface AccountAnonymizer {

    void anonymize(UUID userId, UUID requestId);
}
