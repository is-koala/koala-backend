package com.exskylab.koala.core.qa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * İstanbul QA veri paketini yalnız açıkça seçilen sürüm için bir kez uygular.
 *
 * <p>Normal servis açılışında {@code QA_MARKETPLACE_SEED_VERSION} boş olduğu
 * için bu bean oluşturulmaz. Sürüm ortamda yanlışlıkla bırakılırsa seed geçmişi
 * aynı paketin sonraki restart'larda yeniden çalışmasını engeller.</p>
 */
@Component
@ConditionalOnProperty(
        name = "qa.marketplace-seed.version",
        havingValue = QaMarketplaceSeeder.DATASET_VERSION)
public class QaMarketplaceSeedRunner implements ApplicationRunner {

    private final QaMarketplaceSeeder seeder;
    private final String workerEmail;
    private final String workerPassword;
    private final String employerEmail;
    private final String employerPassword;
    private final String publicBaseUrl;

    public QaMarketplaceSeedRunner(
            QaMarketplaceSeeder seeder,
            @Value("${qa.marketplace-seed.worker-email:}") String workerEmail,
            @Value("${qa.marketplace-seed.worker-password:}") String workerPassword,
            @Value("${qa.marketplace-seed.employer-email:}") String employerEmail,
            @Value("${qa.marketplace-seed.employer-password:}") String employerPassword,
            @Value("${app.api-url}") String publicBaseUrl) {
        this.seeder = seeder;
        this.workerEmail = workerEmail;
        this.workerPassword = workerPassword;
        this.employerEmail = employerEmail;
        this.employerPassword = employerPassword;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed(new QaMarketplaceSeeder.Credentials(
                QaMarketplaceSeeder.DATASET_VERSION,
                workerEmail,
                workerPassword,
                employerEmail,
                employerPassword,
                publicBaseUrl));
    }
}
