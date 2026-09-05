package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class AuditorAwareImpl implements AuditorAware<User> {

    private final SecurityService securityService;
    private final UserDao userDao;

    public AuditorAwareImpl(SecurityService securityService, UserDao userDao) {
        this.securityService = securityService;
        this.userDao = userDao;
    }

    @Override
    public Optional<User> getCurrentAuditor() {
        UUID auditorId;
        try {
            auditorId = securityService.getAuthenticatedUserId();
        } catch (RuntimeException e) {
            auditorId = SystemUser.ID;
        }
        return Optional.of(userDao.getReferenceById(auditorId));
    }

    @Bean
    public AuditorAware<User> auditorAware() {
        return this;
    }
}
