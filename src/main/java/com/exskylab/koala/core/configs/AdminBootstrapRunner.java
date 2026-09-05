package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.properties.AdminBootstrapProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserService userService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.getEmail();

        if (email == null || email.isBlank()) {
            return;
        }

        String trimmed = email.trim();

        try {
            if (userService.bootstrapAdminRole(trimmed)) {
                log.info("ADMIN_BOOTSTRAP_EMAIL granted ROLE_ADMIN to {}",
                        LogMasks.email(trimmed));
            } else {
                log.info("ADMIN_BOOTSTRAP_EMAIL already holds ROLE_ADMIN, nothing written: {}",
                        LogMasks.email(trimmed));
            }
        } catch (RuntimeException exception) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL could not be granted ROLE_ADMIN: {} ({})",
                    LogMasks.email(trimmed), exception.getMessage());
        }
    }
}
