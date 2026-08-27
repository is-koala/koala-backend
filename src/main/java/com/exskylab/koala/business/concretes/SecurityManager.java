package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.UserNotFoundException;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SecurityManager implements SecurityService {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserDao userDao;

    public SecurityManager(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserPrincipal getAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UserNotFoundException("{user.not.authenticated}");
        }

        return principal;
    }

    @Override
    public UUID getAuthenticatedUserId() {
        return getAuthenticatedPrincipal().id();
    }

    @Override
    @Transactional(readOnly = true)
    public User getAuthenticatedUser() {
        UUID userId = getAuthenticatedUserId();
        return userDao.findById(userId).orElseThrow(() -> new UserNotFoundException("{user.not.found}"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getSystemUser() {
        return userDao.findById(SYSTEM_USER_ID)
                .orElseThrow(() -> new UserNotFoundException("{system.user.not.found}"));
    }
}
