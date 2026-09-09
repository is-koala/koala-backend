package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.User;

import java.util.UUID;

public interface SecurityService {

    UserPrincipal getAuthenticatedPrincipal();

    UUID getAuthenticatedUserId();

    User getAuthenticatedUser();

    User getSystemUser();

    boolean isActiveUser(UUID userId);
}
