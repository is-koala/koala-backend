package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.properties.StaffBootstrapProperties;
import com.exskylab.koala.core.security.StaffBootstrapResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StaffBootstrapRunner implements ApplicationRunner {

    private final StaffBootstrapProperties properties;
    private final StaffAccountService staffAccountService;

    public StaffBootstrapRunner(StaffBootstrapProperties properties, StaffAccountService staffAccountService) {
        this.properties = properties;
        this.staffAccountService = staffAccountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }

        String email = properties.getOwnerEmail().trim();

        try {
            StaffBootstrapResult result =
                    staffAccountService.bootstrapOwner(email, properties.getOwnerPassword());

            if (result.created()) {
                log.info("First OWNER staff member created via BOOTSTRAP_OWNER_EMAIL: {}. "
                                + "Password must change on first login; BOOTSTRAP_OWNER_PASSWORD should now be removed.",
                        LogMasks.email(email));
            } else {
                log.warn("Staff bootstrap skipped, {} staff members exist: no row is overwritten while the table "
                                + "is not empty. BOOTSTRAP_OWNER_EMAIL and BOOTSTRAP_OWNER_PASSWORD should be removed.",
                        result.existingMembers());
            }
        } catch (RuntimeException exception) {
            log.error("Staff bootstrap could not run: {}", LogMasks.email(email), exception);
        }
    }
}
