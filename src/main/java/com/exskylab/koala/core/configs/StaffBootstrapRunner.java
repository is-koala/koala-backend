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
                log.info("BOOTSTRAP_OWNER_EMAIL ile ilk OWNER personeli yaratıldı: {}. "
                                + "Parola ilk girişte değiştirilecek; BOOTSTRAP_OWNER_PASSWORD artık silinmelidir.",
                        LogMasks.email(email));
            } else {
                log.warn("Personel bootstrap atlandı, {} personel var: tablo boş değilken hiçbir kayıt "
                                + "ezilmez. BOOTSTRAP_OWNER_EMAIL ve BOOTSTRAP_OWNER_PASSWORD kaldırılmalıdır.",
                        result.existingMembers());
            }
        } catch (RuntimeException exception) {
            log.error("Personel bootstrap çalıştırılamadı: {}", LogMasks.email(email), exception);
        }
    }
}
