package com.exskylab.koala.core.qa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class QaMarketplaceSeeder {

    public static final String DATASET_VERSION = "istanbul-v1";
    public static final int WORKER_COUNT = 120;
    public static final int EMPLOYER_COUNT = 30;
    public static final int COMPANY_COUNT = 30;
    public static final int JOB_COUNT = 180;

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T09:00:00Z");
    private static final String[] FIRST_NAMES = {
            "Ece", "Mert", "Zeynep", "Can", "Elif", "Berk", "Deniz", "Arda", "Selin", "Emre",
            "Duru", "Kerem", "İrem", "Ozan", "Ceren", "Barış", "Melis", "Kaan", "Naz", "Tolga",
            "Yağmur", "Doruk", "Aslı", "Eren", "Buse", "Umut", "Seda", "Onur", "Ada", "Burak"
    };
    private static final String[] LAST_NAMES = {
            "Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Aydın", "Arslan", "Doğan", "Kılıç",
            "Koç", "Kurt", "Özdemir", "Polat", "Aksoy", "Erdem", "Güneş", "Tekin", "Bulut", "Kaplan"
    };
    private static final String[] COMPANY_NAMES = {
            "Mavi Fincan Kahve", "Galata Etkinlik", "Boğaz Tasarım", "Kadıköy Sahne", "Pati Dostları",
            "Moda Atölye", "Haliç Organizasyon", "Vapur Dijital", "Nişantaşı Stüdyo", "İstanbul Sofrası",
            "Rota Perakende", "Kule Fotoğraf", "Yakamoz Turizm", "Semt Lojistik", "Kuzguncuk Fırın",
            "Beyoğlu Kültür", "Çınar Eğitim", "Marmara Ajans", "Pera Konaklama", "Sahil Spor",
            "Lale Catering", "Karaköy Kreatif", "Ada Evcil Bakım", "Topkapı Müze Hizmetleri", "Ortaköy Etkinlik",
            "Balat Sanat", "Levent Teknoloji", "Üsküdar Kahve", "Bakırköy Yaşam", "Koala İstanbul İşletmesi"
    };
    private static final String[] JOB_TITLES = {
            "Etkinlik Fotoğrafçısı", "Servis Destek Görevlisi", "Köpek Gezdirme Desteği",
            "Sosyal Medya Tasarımcısı", "Deneyimli Barista", "Festival Karşılama Ekibi",
            "Ürün Çekimi Asistanı", "Kafe Servis Elemanı", "Kedi Bakım Görevlisi", "Afiş Tasarım Desteği",
            "Kahve Standı Görevlisi", "Davet Organizasyon Asistanı", "Mekân Fotoğrafçısı",
            "Yoğun Saat Garsonu", "Günlük Tasarım Asistanı"
    };
    private static final String[] JOB_DESCRIPTIONS = {
            "Samimi ekibimize gün boyunca destek olacak, iletişimi güçlü bir ekip arkadaşı arıyoruz.",
            "İstanbul'daki etkinliğimizde misafir deneyimini önemseyen, çözüm odaklı çalışma arkadaşı arıyoruz.",
            "Kısa süreli bu görevde düzenli, dakik ve ekip çalışmasına yatkın adaylarla tanışmak istiyoruz.",
            "Markamızın günlük operasyonuna destek verecek, enerjik ve sorumluluk sahibi ekip arkadaşı arıyoruz.",
            "Görev öncesinde kısa bir bilgilendirme yapılacaktır; gerekli ekipman işletmemiz tarafından sağlanır."
    };
    private static final UUID[] CATEGORY_IDS = {
            UUID.fromString("9a1c0f10-0001-4c00-8000-000000000001"),
            UUID.fromString("9a1c0f10-0001-4c00-8000-000000000002"),
            UUID.fromString("9a1c0f10-0001-4c00-8000-000000000003"),
            UUID.fromString("9a1c0f10-0001-4c00-8000-000000000004"),
            UUID.fromString("9a1c0f10-0001-4c00-8000-000000000005")
    };
    private static final String[] CATEGORY_NAMES = {
            "Fotoğrafçılık", "Servis", "Hayvan Bakımı", "Tasarım", "Barista"
    };

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public QaMarketplaceSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean seed(Credentials credentials) {
        validate(credentials);
        int claimed = jdbcTemplate.update("""
                insert into qa_marketplace_seed_versions (version, applied_at)
                values (?, now())
                on conflict (version) do nothing
                """, credentials.version());
        if (claimed == 0) {
            log.info("QA marketplace dataset {} was already applied; leaving all records untouched.",
                    credentials.version());
            return false;
        }

        UUID countryId = jdbcTemplate.queryForObject(
                "select id from countries where code = 'TR'", UUID.class);
        UUID cityId = jdbcTemplate.queryForObject(
                "select id from cities where country_id = ? and name = 'İSTANBUL'", UUID.class, countryId);
        List<District> districts = jdbcTemplate.query(
                "select id, name from districts where city_id = ? and is_deleted = false order by name",
                (resultSet, rowNumber) -> new District(
                        resultSet.getObject("id", UUID.class), resultSet.getString("name")),
                cityId);

        if (districts.isEmpty()) {
            throw new IllegalStateException("İstanbul district reference data is missing");
        }

        String workerPassword = passwordEncoder.encode(credentials.workerPassword());
        String employerPassword = passwordEncoder.encode(credentials.employerPassword());
        String unavailablePassword = passwordEncoder.encode(UUID.randomUUID().toString());
        String assetBaseUrl = normalizedBaseUrl(credentials.publicBaseUrl());
        SeedUserIds userIds = new SeedUserIds(
                resolveActiveUserId(credentials.workerEmail(), id("worker", 0)),
                resolveActiveUserId(credentials.employerEmail(), id("employer", 0)));

        seedWorkers(countryId, cityId, districts, credentials.workerEmail(), workerPassword,
                unavailablePassword, assetBaseUrl, userIds);
        seedEmployers(countryId, cityId, districts, credentials.employerEmail(), employerPassword,
                unavailablePassword, assetBaseUrl, userIds);
        seedCompanies(countryId, cityId, districts, assetBaseUrl, userIds);
        seedJobs(countryId, cityId, districts, assetBaseUrl, userIds);
        seedMarketplaceActivity(userIds);
        log.info("QA marketplace dataset {} is ready: {} workers, {} employers, {} companies and {} jobs.",
                credentials.version(), WORKER_COUNT, EMPLOYER_COUNT, COMPANY_COUNT, JOB_COUNT);
        return true;
    }

    private void seedWorkers(UUID countryId, UUID cityId, List<District> districts,
                             String qaEmail, String qaPassword, String unavailablePassword,
                             String assetBaseUrl, SeedUserIds userIds) {
        for (int index = 0; index < WORKER_COUNT; index++) {
            UUID userId = userIds.worker(index);
            UUID addressId = id("worker-address", index);
            UUID avatarId = id("worker-avatar", index);
            UUID cvId = id("worker-cv", index);
            District district = districts.get(index % districts.size());
            String firstName = index == 0 ? "Ece" : FIRST_NAMES[index % FIRST_NAMES.length];
            String lastName = index == 0 ? "Yılmaz" : LAST_NAMES[(index * 3) % LAST_NAMES.length];
            String email = index == 0 ? qaEmail : "calisan%03d@qa.iskoala.invalid".formatted(index);
            String password = index == 0 ? qaPassword : unavailablePassword;

            upsertAddress(addressId, countryId, cityId, district, index, "worker");
            upsertMedia(avatarId, "IMAGE", "calisan-%03d.png".formatted(index), "image/png",
                    assetBaseUrl + "/qa-assets/avatars/calisan-%02d.png".formatted(index % 12), 180_000L);
            upsertMedia(cvId, "FILE", "calisan-%03d-cv.pdf".formatted(index), "application/pdf",
                    assetBaseUrl + "/qa-assets/cv/ornek-cv.pdf", 92_000L);

            jdbcTemplate.update("""
                    insert into users (
                        id, created_at, first_name, last_name, phone_number, username, email,
                        profile_picture_id, cv_id, password, birth_date, gender, tc_identity_number,
                        tc_document_number, job_title, about, qualifications, interests,
                        email_verified, phone_verified, identity_verified, is_employer,
                        iyzico_submerchant_key, address_id, is_deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, true, true,
                              false, ?, ?, false)
                    on conflict (id) do update set
                        first_name = excluded.first_name, last_name = excluded.last_name,
                        phone_number = excluded.phone_number, username = excluded.username,
                        email = excluded.email, profile_picture_id = excluded.profile_picture_id,
                        cv_id = excluded.cv_id, password = excluded.password,
                        birth_date = excluded.birth_date, gender = excluded.gender,
                        tc_identity_number = excluded.tc_identity_number,
                        tc_document_number = excluded.tc_document_number,
                        job_title = excluded.job_title, about = excluded.about,
                        qualifications = excluded.qualifications, interests = excluded.interests,
                        email_verified = true, phone_verified = true, identity_verified = true,
                        is_employer = false,
                        iyzico_submerchant_key = users.iyzico_submerchant_key,
                        address_id = excluded.address_id, is_deleted = false, deleted_at = null
                    """,
                    userId, Timestamp.from(CREATED_AT.plusSeconds(index * 300L)), firstName, lastName,
                    "+90530%07d".formatted(index + 1000), "calisan.%03d".formatted(index), email,
                    avatarId, cvId, password, Date.valueOf(LocalDate.of(1992 + index % 10, 1 + index % 12, 1 + index % 27)),
                    index % 3 == 0 ? "FEMALE" : index % 3 == 1 ? "MALE" : "NOT_SPECIFIED",
                    turkishIdentity(index), "D%08d".formatted(index + 1),
                    CATEGORY_NAMES[index % CATEGORY_NAMES.length] + " Uzmanı",
                    "İstanbul'da esnek işlerde çalışan, dakik ve iletişimi güçlü bir Koala üyesiyim.",
                    index % 2 == 0 ? "Müşteri iletişimi, ekip çalışması, zaman yönetimi" : "Etkinlik deneyimi, çözüm odaklılık, dikkat",
                    "Kahve, müzik, şehir etkinlikleri", null, addressId);

            addRole(userId);
            upsertBankAccount(userId, firstName + " " + lastName, index);
        }
    }

    private void seedEmployers(UUID countryId, UUID cityId, List<District> districts,
                               String qaEmail, String qaPassword, String unavailablePassword,
                               String assetBaseUrl, SeedUserIds userIds) {
        for (int index = 0; index < EMPLOYER_COUNT; index++) {
            UUID userId = userIds.employer(index);
            UUID addressId = id("employer-address", index);
            UUID avatarId = id("employer-avatar", index);
            District district = districts.get((index * 2 + 3) % districts.size());
            String firstName = index == 0 ? "Mert" : FIRST_NAMES[(index + 7) % FIRST_NAMES.length];
            String lastName = index == 0 ? "Kaya" : LAST_NAMES[(index * 5 + 2) % LAST_NAMES.length];
            String email = index == 0 ? qaEmail : "isveren%03d@qa.iskoala.invalid".formatted(index);
            String password = index == 0 ? qaPassword : unavailablePassword;

            upsertAddress(addressId, countryId, cityId, district, index, "employer");
            upsertMedia(avatarId, "IMAGE", "isveren-%03d.png".formatted(index), "image/png",
                    assetBaseUrl + "/qa-assets/avatars/isveren-%02d.png".formatted(index % 6), 170_000L);

            jdbcTemplate.update("""
                    insert into users (
                        id, created_at, first_name, last_name, phone_number, username, email,
                        profile_picture_id, password, birth_date, gender, tc_identity_number,
                        tc_document_number, job_title, about, qualifications, interests,
                        email_verified, phone_verified, identity_verified, is_employer,
                        iyzico_submerchant_key, address_id, is_deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, true, true,
                              true, ?, ?, false)
                    on conflict (id) do update set
                        first_name = excluded.first_name, last_name = excluded.last_name,
                        phone_number = excluded.phone_number, username = excluded.username,
                        email = excluded.email, profile_picture_id = excluded.profile_picture_id,
                        password = excluded.password, birth_date = excluded.birth_date,
                        gender = excluded.gender, tc_identity_number = excluded.tc_identity_number,
                        tc_document_number = excluded.tc_document_number, job_title = excluded.job_title,
                        about = excluded.about, qualifications = excluded.qualifications,
                        interests = excluded.interests, email_verified = true, phone_verified = true,
                        identity_verified = true, is_employer = true,
                        iyzico_submerchant_key = users.iyzico_submerchant_key,
                        address_id = excluded.address_id, is_deleted = false, deleted_at = null
                    """,
                    userId, Timestamp.from(CREATED_AT.plusSeconds((index + 120L) * 300L)), firstName, lastName,
                    "+90540%07d".formatted(index + 1000), "isveren.%03d".formatted(index), email,
                    avatarId, password, Date.valueOf(LocalDate.of(1984 + index % 12, 1 + index % 12, 1 + index % 27)),
                    index % 2 == 0 ? "MALE" : "FEMALE", turkishIdentity(500 + index),
                    "E%08d".formatted(index + 1), "İşletme Yöneticisi",
                    "İstanbul'da güvenilir ekip arkadaşlarıyla çalışan yerel bir işletme yöneticisiyim.",
                    "Operasyon, ekip yönetimi, etkinlik planlama", "Yerel işletmeler, sürdürülebilirlik",
                    null, addressId);
            addRole(userId);
        }
    }

    private void seedCompanies(UUID countryId, UUID cityId, List<District> districts,
                               String assetBaseUrl, SeedUserIds userIds) {
        String[] types = {"LIMITED", "CORPORATION", "STARTUP", "AGENCY", "SOLE_PROPRIETORSHIP"};
        for (int index = 0; index < 30; index++) {
            UUID companyId = id("company", index);
            UUID addressId = id("company-address", index);
            UUID logoId = id("company-logo", index);
            UUID employerId = userIds.employer(index);
            District district = districts.get((index * 3 + 5) % districts.size());

            upsertAddress(addressId, countryId, cityId, district, index + 30, "company");
            upsertMedia(logoId, "IMAGE", "sirket-%02d-logo.png".formatted(index), "image/png",
                    assetBaseUrl + "/qa-assets/companies/sirket-%02d.png".formatted(index), 210_000L);
            jdbcTemplate.update("""
                    insert into companies (
                        id, created_at, name, email, phone_number, logo_id, website, description,
                        address_id, tax_number, type, is_approved, iyzico_submerchant_key, is_deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, false)
                    on conflict (id) do update set
                        name = excluded.name, email = excluded.email, phone_number = excluded.phone_number,
                        logo_id = excluded.logo_id, website = excluded.website,
                        description = excluded.description, address_id = excluded.address_id,
                        tax_number = excluded.tax_number, type = excluded.type, is_approved = true,
                        iyzico_submerchant_key = companies.iyzico_submerchant_key,
                        is_deleted = false, deleted_at = null
                    """,
                    companyId, Timestamp.from(CREATED_AT.plusSeconds(index * 900L)), COMPANY_NAMES[index],
                    "sirket%02d@qa.iskoala.invalid".formatted(index), "+90212%07d".formatted(index + 2000),
                    logoId, "https://qa.iskoala.invalid/sirket/%02d".formatted(index),
                    "%s, %s çevresinde etkinlik ve günlük operasyon hizmetleri sunan yerel bir işletmedir."
                            .formatted(COMPANY_NAMES[index], district.name()),
                    addressId, "%010d".formatted(100_000_000L + index), types[index % types.length], null);

            jdbcTemplate.update("""
                    insert into company_contacts (id, company_id, user_id, role, created_at, is_deleted)
                    values (?, ?, ?, 'ADMIN', ?, false)
                    on conflict (id) do update set company_id = excluded.company_id,
                        user_id = excluded.user_id, role = 'ADMIN', is_deleted = false, deleted_at = null
                    """, id("company-contact", index), companyId, employerId, Timestamp.from(CREATED_AT));
            jdbcTemplate.update("""
                    insert into company_images (id, company_id, image_id, sort_order, is_cover, caption, created_at, is_deleted)
                    values (?, ?, ?, 0, true, ?, ?, false)
                    on conflict (id) do update set company_id = excluded.company_id, image_id = excluded.image_id,
                        sort_order = 0, is_cover = true, caption = excluded.caption, is_deleted = false, deleted_at = null
                    """, id("company-image", index), companyId, logoId, COMPANY_NAMES[index] + " mekânı",
                    Timestamp.from(CREATED_AT));
        }
    }

    private void seedJobs(UUID countryId, UUID cityId, List<District> districts,
                          String assetBaseUrl, SeedUserIds userIds) {
        LocalDateTime base = LocalDate.now(ISTANBUL).atTime(9, 0).plusDays(3);
        for (int index = 0; index < 180; index++) {
            int companyIndex = index % 30;
            UUID companyId = id("company", companyIndex);
            UUID employerId = userIds.employer(companyIndex);
            UUID addressId = id("job-address", index);
            UUID jobId = id("job", index);
            UUID imageId = id("job-image-media", index);
            District district = districts.get((index * 7 + 1) % districts.size());
            String status = jobStatus(index);
            boolean historic = status.equals("COMPLETED") || status.equals("CANCELLED");
            LocalDateTime start = historic
                    ? base.minusDays(45L + index % 14)
                    : base.plusDays(index % 45).plusHours(index % 8);
            LocalDateTime end = start.plusHours(4 + index % 5);
            int categoryIndex = index % CATEGORY_IDS.length;
            BigDecimal salary = BigDecimal.valueOf(750L + (index % 12) * 125L);
            BigDecimal commission = salary.multiply(new BigDecimal("0.075"))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = salary.add(commission);

            upsertAddress(addressId, countryId, cityId, district, index + 100, "job");
            upsertMedia(imageId, "IMAGE", "ilan-%03d.png".formatted(index), "image/png",
                    assetBaseUrl + "/qa-assets/jobs/kategori-%02d.png".formatted(categoryIndex), 320_000L);

            jdbcTemplate.update("""
                    insert into jobs (
                        id, created_at, company_id, creator_id, title, description, sector, position,
                        location, address_id, type, start_date_time, end_date_time, salary_type,
                        salary, pricing_version, currency, worker_payout, commission_amount,
                        commission_rate, total_charged, min_age, max_age, experience_required,
                        headcount, dress_code, status,
                        employer_type, insurance_required, job_category_id, is_deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-09-v1', 'TRY', ?, ?, 0.075, ?,
                              ?, ?, ?, ?, ?, ?,
                              'COMPANY', ?, ?, false)
                    on conflict (id) do update set
                        company_id = excluded.company_id, creator_id = excluded.creator_id,
                        title = excluded.title, description = excluded.description, sector = excluded.sector,
                        position = excluded.position, location = excluded.location, address_id = excluded.address_id,
                        type = excluded.type, start_date_time = excluded.start_date_time,
                        end_date_time = excluded.end_date_time, salary_type = excluded.salary_type,
                        salary = excluded.salary, pricing_version = excluded.pricing_version,
                        currency = excluded.currency, worker_payout = excluded.worker_payout,
                        commission_amount = excluded.commission_amount,
                        commission_rate = excluded.commission_rate,
                        total_charged = excluded.total_charged,
                        min_age = excluded.min_age, max_age = excluded.max_age,
                        experience_required = excluded.experience_required, headcount = excluded.headcount,
                        dress_code = excluded.dress_code, status = excluded.status,
                        employer_type = excluded.employer_type, insurance_required = excluded.insurance_required,
                        job_category_id = excluded.job_category_id, is_deleted = false, deleted_at = null
                    """,
                    jobId, Timestamp.from(CREATED_AT.plusSeconds(index * 1200L)), companyId, employerId,
                    JOB_TITLES[index % JOB_TITLES.length] + " · " + district.name(),
                    JOB_DESCRIPTIONS[index % JOB_DESCRIPTIONS.length], CATEGORY_NAMES[categoryIndex],
                    JOB_TITLES[index % JOB_TITLES.length],
                    "Karşılama, ekip koordinasyonu ve görev sonu kısa raporlama.", addressId,
                    index % 4 == 0 ? "RECURRING" : "ONE_TIME", Timestamp.valueOf(start), Timestamp.valueOf(end),
                    index % 3 == 0 ? "HOURLY" : "DAILY", salary, salary, commission, total, 18, 55,
                    index % 3 == 0, 1 + index % 4, index % 2 == 0 ? "Smart casual" : "Rahat ve temiz",
                    status, index % 4 == 0, CATEGORY_IDS[categoryIndex]);

            jdbcTemplate.update("""
                    insert into job_images (id, job_id, image_id, sort_order, is_cover, caption, created_at, is_deleted)
                    values (?, ?, ?, 0, true, ?, ?, false)
                    on conflict (id) do update set job_id = excluded.job_id, image_id = excluded.image_id,
                        sort_order = 0, is_cover = true, caption = excluded.caption,
                        is_deleted = false, deleted_at = null
                    """, id("job-image", index), jobId, imageId, JOB_TITLES[index % JOB_TITLES.length],
                    Timestamp.from(CREATED_AT));
        }
    }

    private void seedMarketplaceActivity(SeedUserIds userIds) {
        seedApplications(userIds);
        seedAssignmentsAndPayments(userIds);
        seedFollowsAndSavedJobs(userIds);
        seedNotifications(userIds);
        seedRatings(userIds);
    }

    private void seedApplications(SeedUserIds userIds) {
        for (int jobIndex = 0; jobIndex < 180; jobIndex++) {
            for (int slot = 0; slot < 2; slot++) {
                int workerIndex = workerIndex(jobIndex, slot);
                String status = isAssignedJob(jobIndex) && slot == 0
                        ? "ACCEPTED"
                        : (jobIndex + slot) % 4 == 0 ? "REJECTED" : "APPLIED";
                upsertApplication(jobIndex, slot, workerIndex, status, userIds);
            }
        }
    }

    private void seedAssignmentsAndPayments(SeedUserIds userIds) {
        int[] jobIndexes = assignmentJobIndexes();
        String[] assignmentStatuses = {
                "PENDING_CONFIRMATION", "CONFIRMED", "REJECTED", "IN_PROGRESS", "CANCELLED", "COMPLETED"
        };
        String[] paymentStatuses = {
                "PENDING_COLLECTION", "HELD_IN_ESCROW", "RELEASED_TO_WORKER", "FAILED", "REFUNDED"
        };

        for (int sequence = 0; sequence < jobIndexes.length; sequence++) {
            int jobIndex = jobIndexes[sequence];
            int workerIndex = workerIndex(jobIndex, 0);
            UUID assignmentId = id("assignment", jobIndex);
            UUID jobId = id("job", jobIndex);
            UUID workerId = userIds.worker(workerIndex);
            UUID applicationId = id("application", jobIndex * 10);
            String assignmentStatus = assignmentStatuses[sequence / 10];
            String paymentStatus = paymentStatuses[sequence % paymentStatuses.length];
            Instant assignedAt = CREATED_AT.plusSeconds(100_000L + sequence * 1_800L);
            Instant expectedAt = assignedAt.plusSeconds(86_400L * 7);
            Instant completedAt = assignmentStatus.equals("COMPLETED") ? expectedAt : null;
            BigDecimal workerPayout = BigDecimal.valueOf(1_000L + (jobIndex % 10) * 100L);
            BigDecimal commission = workerPayout.multiply(new BigDecimal("0.075"));
            BigDecimal total = workerPayout.add(commission);

            jdbcTemplate.update("""
                    insert into job_assignments (
                        id, job_id, worker_id, job_application_id, status, payment_status,
                        assignment_date_time, expected_completion_date_time, actual_completion_date_time,
                        work_hours, final_payout, pricing_version, currency, worker_payout,
                        commission_amount, commission_rate, total_charged, notes,
                        iyzico_submerchant_key, created_at, is_deleted
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-09-v1', 'TRY', ?, ?, 0.075, ?, ?, ?, ?, false)
                    on conflict (id) do update set
                        job_id = excluded.job_id, worker_id = excluded.worker_id,
                        job_application_id = excluded.job_application_id, status = excluded.status,
                        payment_status = excluded.payment_status,
                        assignment_date_time = excluded.assignment_date_time,
                        expected_completion_date_time = excluded.expected_completion_date_time,
                        actual_completion_date_time = excluded.actual_completion_date_time,
                        work_hours = excluded.work_hours, final_payout = excluded.final_payout,
                        pricing_version = excluded.pricing_version, currency = excluded.currency,
                        worker_payout = excluded.worker_payout, commission_amount = excluded.commission_amount,
                        commission_rate = excluded.commission_rate, total_charged = excluded.total_charged,
                        notes = excluded.notes, iyzico_submerchant_key = excluded.iyzico_submerchant_key,
                        is_deleted = false, deleted_at = null
                    """,
                    assignmentId, jobId, workerId, applicationId, assignmentStatus, paymentStatus,
                    Timestamp.from(assignedAt), Timestamp.from(expectedAt), timestamp(completedAt),
                    assignmentStatus.equals("COMPLETED") ? 8 : 0,
                    assignmentStatus.equals("COMPLETED") ? workerPayout : null,
                    workerPayout, commission, total, "Koala QA görünüm ataması · " + assignmentStatus,
                    null, Timestamp.from(assignedAt));

            seedPayment(sequence, assignmentId, paymentStatus, workerPayout, commission, total);
            seedConversation(jobIndex, workerIndex, sequence, userIds);

            if (assignmentStatus.equals("IN_PROGRESS") || assignmentStatus.equals("COMPLETED")) {
                seedWorkSession(jobIndex, assignmentId, assignmentStatus.equals("COMPLETED"));
            }
            if (assignmentStatus.equals("COMPLETED")) {
                seedReviews(jobIndex, assignmentId, workerId, userIds);
            }
        }
    }

    private void seedPayment(int sequence, UUID assignmentId, String paymentStatus,
                             BigDecimal workerPayout, BigDecimal commission, BigDecimal total) {
        String attemptStatus = switch (paymentStatus) {
            case "FAILED" -> "FAILED";
            case "PENDING_COLLECTION" -> "INITIATED";
            case "HELD_IN_ESCROW" -> "IN_REVIEW";
            default -> "SUCCEEDED";
        };
        Instant completedAt = attemptStatus.equals("SUCCEEDED") ? CREATED_AT.plusSeconds(220_000L + sequence * 600L) : null;
        jdbcTemplate.update("""
                insert into payments (
                    id, job_assignment_id, conversation_id, provider_token, status, currency,
                    pricing_version, worker_payout, commission_amount, commission_rate, total_charged,
                    provider_payment_id, provider_transaction_id, failure_reason, completed_at,
                    created_at, is_deleted
                ) values (?, ?, ?, ?, ?, 'TRY', '2026-09-v1', ?, ?, 0.075, ?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set
                    job_assignment_id = excluded.job_assignment_id,
                    conversation_id = excluded.conversation_id, provider_token = excluded.provider_token,
                    status = excluded.status, currency = 'TRY', pricing_version = excluded.pricing_version,
                    worker_payout = excluded.worker_payout,
                    commission_amount = excluded.commission_amount, commission_rate = excluded.commission_rate,
                    total_charged = excluded.total_charged,
                    provider_payment_id = excluded.provider_payment_id,
                    provider_transaction_id = excluded.provider_transaction_id,
                    failure_reason = excluded.failure_reason, completed_at = excluded.completed_at,
                    is_deleted = false, deleted_at = null
                """,
                id("payment", sequence), assignmentId, "qa-state-fixture-%03d".formatted(sequence),
                null, attemptStatus, workerPayout, commission, total,
                null, null,
                attemptStatus.equals("FAILED") ? "QA durum görünümü; sağlayıcı işlemi değildir" : null,
                timestamp(completedAt), Timestamp.from(CREATED_AT.plusSeconds(200_000L + sequence * 600L)));
    }

    private void seedConversation(int jobIndex, int workerIndex, int sequence, SeedUserIds userIds) {
        UUID conversationId = id("conversation", jobIndex);
        UUID jobId = id("job", jobIndex);
        UUID employerId = userIds.employer(jobIndex % 30);
        UUID workerId = userIds.worker(workerIndex);
        Instant lastMessageAt = CREATED_AT.plusSeconds(300_000L + sequence * 900L);
        String lastBody = "Harika, görev günü görüşmek üzere.";
        jdbcTemplate.update("""
                insert into conversations (
                    id, job_id, employer_id, worker_id, last_message_at, last_message_body,
                    kind, created_at, is_deleted
                ) values (?, ?, ?, ?, ?, ?, 'DIRECT', ?, false)
                on conflict (id) do update set
                    job_id = excluded.job_id, employer_id = excluded.employer_id,
                    worker_id = excluded.worker_id, last_message_at = excluded.last_message_at,
                    last_message_body = excluded.last_message_body, kind = 'DIRECT',
                    is_deleted = false, deleted_at = null
                """, conversationId, jobId, employerId, workerId, Timestamp.from(lastMessageAt), lastBody,
                Timestamp.from(lastMessageAt.minusSeconds(3_600)));

        upsertParticipant(conversationId, employerId, "employer", jobIndex);
        upsertParticipant(conversationId, workerId, "worker", jobIndex);
        upsertMessage(conversationId, employerId, jobIndex, 0,
                "Merhaba, başvurun için teşekkürler. Saat bilgisi senin için uygun mu?", lastMessageAt.minusSeconds(1_800));
        upsertMessage(conversationId, workerId, jobIndex, 1,
                "Merhaba, evet uygunum. Görev başlangıcından 15 dakika önce orada olurum.", lastMessageAt.minusSeconds(900));
        upsertMessage(conversationId, employerId, jobIndex, 2, lastBody, lastMessageAt);
    }

    private void seedWorkSession(int jobIndex, UUID assignmentId, boolean completed) {
        Instant checkIn = CREATED_AT.plusSeconds(420_000L + jobIndex * 600L);
        Instant checkOut = completed ? checkIn.plusSeconds(8 * 3_600L) : null;
        jdbcTemplate.update("""
                insert into work_sessions (
                    id, job_assignment_id, check_in_at, check_out_at,
                    check_in_latitude, check_in_longitude, check_out_latitude,
                    check_out_longitude, minutes, created_at, is_deleted
                ) values (?, ?, ?, ?, 41.0082, 28.9784, ?, ?, ?, ?, false)
                on conflict (id) do update set
                    job_assignment_id = excluded.job_assignment_id,
                    check_in_at = excluded.check_in_at, check_out_at = excluded.check_out_at,
                    check_in_latitude = excluded.check_in_latitude,
                    check_in_longitude = excluded.check_in_longitude,
                    check_out_latitude = excluded.check_out_latitude,
                    check_out_longitude = excluded.check_out_longitude,
                    minutes = excluded.minutes, is_deleted = false, deleted_at = null
                """, id("work-session", jobIndex), assignmentId, Timestamp.from(checkIn), timestamp(checkOut),
                completed ? new BigDecimal("41.0083") : null,
                completed ? new BigDecimal("28.9785") : null,
                completed ? 480 : null, Timestamp.from(checkIn));
    }

    private void seedReviews(int jobIndex, UUID assignmentId, UUID workerId, SeedUserIds userIds) {
        UUID employerId = userIds.employer(jobIndex % 30);
        upsertReview(assignmentId, employerId, workerId, jobIndex, 0,
                4 + jobIndex % 2, "Dakik, iletişimi kuvvetli ve görev boyunca çok yardımcıydı.", false);
        upsertReview(assignmentId, workerId, employerId, jobIndex, 1,
                4 + (jobIndex + 1) % 2, "Görev tanımı netti, ekip çok ilgiliydi. Tekrar çalışmak isterim.", jobIndex % 3 == 0);
    }

    private void seedFollowsAndSavedJobs(SeedUserIds userIds) {
        for (int workerIndex = 0; workerIndex < 120; workerIndex++) {
            UUID workerId = userIds.worker(workerIndex);
            for (int slot = 0; slot < 2; slot++) {
                int companyIndex = (workerIndex * 3 + slot * 7) % 30;
                jdbcTemplate.update("""
                        insert into company_follows (id, follower_id, company_id, created_at, is_deleted)
                        values (?, ?, ?, ?, false)
                        on conflict (company_id, follower_id) where is_deleted = false
                        do update set is_deleted = false, deleted_at = null
                        """, id("follow", workerIndex * 10 + slot), workerId, id("company", companyIndex),
                        Timestamp.from(CREATED_AT.plusSeconds(workerIndex * 60L)));

                UUID jobId = id("job", (workerIndex * 5 + slot * 11) % 120);
                jdbcTemplate.update("""
                        insert into users_saved_jobs (user_id, job_id)
                        select ?, ? where not exists (
                            select 1 from users_saved_jobs where user_id = ? and job_id = ?)
                        """, workerId, jobId, workerId, jobId);
            }
        }
    }

    private void seedNotifications(SeedUserIds userIds) {
        for (int index = 0; index < 20; index++) {
            UUID recipientId = index % 2 == 0 ? userIds.worker(0) : userIds.employer(0);
            UUID targetId = id("job", index);
            jdbcTemplate.update("""
                    insert into notifications (
                        id, recipient_id, channel, category, subject, body, target_type,
                        target_id, status, sent_at, read_at, created_at, is_deleted
                    ) values (?, ?, 'IN_APP', 'APPLICATION_UPDATES', ?, ?, 'JOB', ?, 'SENT', ?, ?, ?, false)
                    on conflict (id) do update set recipient_id = excluded.recipient_id,
                        subject = excluded.subject, body = excluded.body, target_type = excluded.target_type,
                        target_id = excluded.target_id, status = 'SENT', sent_at = excluded.sent_at,
                        read_at = excluded.read_at, is_deleted = false, deleted_at = null
                    """, id("notification", index), recipientId,
                    index % 2 == 0 ? "Yeni iş fırsatı" : "Başvuru hareketi",
                    index % 2 == 0
                            ? "Yakınındaki yeni bir iş ilanı profilinle eşleşiyor."
                            : "İlanlarından birine yeni bir başvuru geldi.",
                    targetId, Timestamp.from(CREATED_AT.plusSeconds(500_000L + index * 600L)),
                    index < 6 ? Timestamp.from(CREATED_AT.plusSeconds(510_000L + index * 600L)) : null,
                    Timestamp.from(CREATED_AT.plusSeconds(500_000L + index * 600L)));
        }
    }

    private void seedRatings(SeedUserIds userIds) {
        for (int index = 0; index < 120; index++) {
            UUID userId = userIds.worker(index);
            int reviewCount = index < 10 ? 1 : 0;
            int rating = index < 10 ? 4 + index % 2 : 0;
            upsertRating(userId, index, reviewCount, rating);
        }
        for (int index = 0; index < 30; index++) {
            UUID userId = userIds.employer(index);
            int reviewCount = index % 3 == 0 ? 1 : 0;
            int rating = reviewCount == 1 ? 4 + index % 2 : 0;
            upsertRating(userId, 200 + index, reviewCount, rating);
        }
    }

    private void upsertApplication(int jobIndex, int slot, int workerIndex, String status,
                                   SeedUserIds userIds) {
        UUID applicationId = id("application", jobIndex * 10 + slot);
        Instant appliedAt = CREATED_AT.plusSeconds(50_000L + jobIndex * 300L + slot * 60L);
        jdbcTemplate.update("""
                insert into job_applications (
                    id, created_at, job_id, user_id, application_date_time,
                    status, notes, answered_date_time, rejection_reason, is_deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set job_id = excluded.job_id, user_id = excluded.user_id,
                    application_date_time = excluded.application_date_time, status = excluded.status,
                    notes = excluded.notes, answered_date_time = excluded.answered_date_time,
                    rejection_reason = excluded.rejection_reason, is_deleted = false, deleted_at = null
                """, applicationId, Timestamp.from(appliedAt), id("job", jobIndex), userIds.worker(workerIndex),
                Timestamp.from(appliedAt), status,
                slot == 0 ? "Merhaba, bu görev için uygunum ve benzer işlerde deneyimliyim." : "İlanınızla ilgileniyorum; belirtilen saatlerde müsaitim.",
                status.equals("APPLIED") ? null : Timestamp.from(appliedAt.plusSeconds(3_600)),
                status.equals("REJECTED") ? "Bu görev için başka bir adayla ilerliyoruz." : null);
    }

    private void upsertReview(UUID assignmentId, UUID authorId, UUID subjectId,
                              int jobIndex, int slot, int rating, String comment, boolean anonymous) {
        jdbcTemplate.update("""
                insert into reviews (
                    id, job_assignment_id, author_id, subject_id, rating, comment,
                    anonymous, created_at, is_deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set job_assignment_id = excluded.job_assignment_id,
                    author_id = excluded.author_id, subject_id = excluded.subject_id,
                    rating = excluded.rating, comment = excluded.comment, anonymous = excluded.anonymous,
                    is_deleted = false, deleted_at = null
                """, id("review", jobIndex * 10 + slot), assignmentId, authorId, subjectId, rating, comment,
                anonymous, Timestamp.from(CREATED_AT.plusSeconds(600_000L + jobIndex * 600L)));
    }

    private void upsertRating(UUID userId, int index, int reviewCount, int ratingSum) {
        BigDecimal average = reviewCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(ratingSum);
        jdbcTemplate.update("""
                insert into rating_summaries (
                    id, user_id, review_count, rating_sum, average_rating, created_at, is_deleted
                ) values (?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set user_id = excluded.user_id,
                    review_count = excluded.review_count, rating_sum = excluded.rating_sum,
                    average_rating = excluded.average_rating, is_deleted = false, deleted_at = null
                """, id("rating", index), userId, reviewCount, ratingSum, average, Timestamp.from(CREATED_AT));
    }

    private void upsertParticipant(UUID conversationId, UUID userId, String role, int jobIndex) {
        jdbcTemplate.update("""
                insert into conversation_participants (
                    id, conversation_id, user_id, joined_at, created_at, is_deleted
                ) values (?, ?, ?, ?, ?, false)
                on conflict (id) do update set conversation_id = excluded.conversation_id,
                    user_id = excluded.user_id, joined_at = excluded.joined_at,
                    is_deleted = false, deleted_at = null
                """, id("participant-" + role, jobIndex), conversationId, userId,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private void upsertMessage(UUID conversationId, UUID senderId, int jobIndex,
                               int slot, String body, Instant sentAt) {
        jdbcTemplate.update("""
                insert into messages (
                    id, conversation_id, sender_id, body, sent_at, read_at, created_at, is_deleted
                ) values (?, ?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set conversation_id = excluded.conversation_id,
                    sender_id = excluded.sender_id, body = excluded.body, sent_at = excluded.sent_at,
                    read_at = excluded.read_at, is_deleted = false, deleted_at = null
                """, id("message", jobIndex * 10 + slot), conversationId, senderId, body,
                Timestamp.from(sentAt), slot < 2 ? Timestamp.from(sentAt.plusSeconds(60)) : null,
                Timestamp.from(sentAt));
    }

    private void upsertAddress(UUID addressId, UUID countryId, UUID cityId,
                               District district, int index, String kind) {
        double latitude = 40.88 + (index % 24) * 0.011;
        double longitude = 28.67 + (index % 31) * 0.018;
        jdbcTemplate.update("""
                insert into addresses (
                    id, created_at, country_id, city_id, district_id, neighborhood,
                    thoroughfare, building_number, postal_code, latitude, longitude,
                    position_source, confirmed_at, canonical_version, needs_review, is_deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USER_CONFIRMED_PIN', ?, 1, false, false)
                on conflict (id) do update set country_id = excluded.country_id,
                    city_id = excluded.city_id, district_id = excluded.district_id,
                    neighborhood = excluded.neighborhood, thoroughfare = excluded.thoroughfare,
                    building_number = excluded.building_number, postal_code = excluded.postal_code,
                    latitude = excluded.latitude, longitude = excluded.longitude,
                    position_source = excluded.position_source, confirmed_at = excluded.confirmed_at,
                    canonical_version = excluded.canonical_version, needs_review = false,
                    is_deleted = false, deleted_at = null
                """, addressId, Timestamp.from(CREATED_AT), countryId, cityId, district.id(),
                titleCase(district.name()) + " Mahallesi", (1 + index % 24) + ". Sokak",
                Integer.toString(2 + index % 90), "%05d".formatted(34000 + index % 900),
                latitude, longitude, Timestamp.from(CREATED_AT));
    }

    private void upsertMedia(UUID mediaId, String type, String fileName,
                             String fileType, String fileUrl, long size) {
        jdbcTemplate.update("""
                insert into media (
                    id, media_type, created_at, file_name, file_type, file_url, file_size, is_deleted
                ) values (?, ?, ?, ?, ?, ?, ?, false)
                on conflict (id) do update set media_type = excluded.media_type,
                    file_name = excluded.file_name, file_type = excluded.file_type,
                    file_url = excluded.file_url, file_size = excluded.file_size,
                    is_deleted = false, deleted_at = null
                """, mediaId, type, Timestamp.from(CREATED_AT), fileName, fileType, fileUrl, size);
    }

    private void upsertBankAccount(UUID userId, String holderName, int index) {
        jdbcTemplate.update("""
                insert into bank_accounts (
                    id, user_id, iban, account_holder_name, is_primary, is_active, created_at, is_deleted
                ) values (?, ?, ?, ?, true, true, ?, false)
                on conflict (id) do update set user_id = excluded.user_id, iban = excluded.iban,
                    account_holder_name = excluded.account_holder_name, is_primary = true,
                    is_active = true, is_deleted = false, deleted_at = null
                """, id("bank-account", index), userId, demoIban(index), holderName, Timestamp.from(CREATED_AT));
    }

    private void addRole(UUID userId) {
        jdbcTemplate.update("""
                insert into authorities (user_id, role)
                select ?, 'ROLE_USER'
                where not exists (
                    select 1 from authorities where user_id = ? and role = 'ROLE_USER')
                """, userId, userId);
    }

    private UUID resolveActiveUserId(String email, UUID fallbackId) {
        List<UUID> matches = jdbcTemplate.queryForList("""
                select id from users
                where email = ? and is_deleted = false
                """, UUID.class, email);
        return matches.isEmpty() ? fallbackId : matches.getFirst();
    }

    private static int workerIndex(int jobIndex, int slot) {
        return 1 + (jobIndex * 7 + slot * 19) % (WORKER_COUNT - 1);
    }

    private static boolean isAssignedJob(int jobIndex) {
        for (int assigned : assignmentJobIndexes()) {
            if (assigned == jobIndex) {
                return true;
            }
        }
        return false;
    }

    private static int[] assignmentJobIndexes() {
        int[] indexes = new int[60];
        for (int i = 0; i < 10; i++) {
            indexes[i] = 1 + i;
            indexes[10 + i] = 11 + i;
            indexes[20 + i] = i == 9 ? 31 : 21 + i;
            indexes[30 + i] = 136 + i;
            indexes[40 + i] = 151 + i;
            indexes[50 + i] = 166 + i;
        }
        return indexes;
    }

    private static String jobStatus(int index) {
        if (index < 120) {
            return "OPEN";
        }
        if (index < 135) {
            return "CLOSED";
        }
        if (index < 150) {
            return "IN_PROGRESS";
        }
        if (index < 165) {
            return "CANCELLED";
        }
        return "COMPLETED";
    }

    private static UUID id(String kind, int index) {
        return UUID.nameUUIDFromBytes(
                ("koala-qa:" + kind + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static String turkishIdentity(int index) {
        if (index == 0) {
            return "11111111111";
        }
        return "%010d%d".formatted(10_000_000L + index, (index % 5) * 2);
    }

    private static String demoIban(int index) {
        if (index == 0) {
            return "TR180006200119000006672315";
        }
        return "TR%024d".formatted(10_000_000_000_000_000L + index);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String lower = value.toLowerCase(new java.util.Locale("tr", "TR"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void validate(Credentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("QA seed credentials are required");
        }
        if (!DATASET_VERSION.equals(credentials.version())) {
            throw new IllegalArgumentException("Unsupported QA marketplace dataset version");
        }
        requireText(credentials.workerEmail(), "QA worker email");
        requireText(credentials.employerEmail(), "QA employer email");
        if (credentials.workerEmail().equalsIgnoreCase(credentials.employerEmail())) {
            throw new IllegalArgumentException("QA worker and employer emails must be different");
        }
        requirePassword(credentials.workerPassword(), "QA worker password");
        requirePassword(credentials.employerPassword(), "QA employer password");

        URI publicBaseUrl;
        try {
            publicBaseUrl = URI.create(credentials.publicBaseUrl());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("QA public base URL is invalid", error);
        }
        if (!("http".equals(publicBaseUrl.getScheme()) || "https".equals(publicBaseUrl.getScheme()))
                || publicBaseUrl.getHost() == null || publicBaseUrl.getUserInfo() != null
                || publicBaseUrl.getQuery() != null || publicBaseUrl.getFragment() != null) {
            throw new IllegalArgumentException("QA public base URL must be an HTTP(S) origin");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requirePassword(String value, String label) {
        requireText(value, label);
        if (value.length() < 12) {
            throw new IllegalArgumentException(label + " must contain at least 12 characters");
        }
    }

    private static String normalizedBaseUrl(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record Credentials(String version, String workerEmail, String workerPassword,
                              String employerEmail, String employerPassword, String publicBaseUrl) {
    }

    private record SeedUserIds(UUID controlledWorkerId, UUID controlledEmployerId) {

        private UUID worker(int index) {
            return index == 0 ? controlledWorkerId : id("worker", index);
        }

        private UUID employer(int index) {
            return index == 0 ? controlledEmployerId : id("employer", index);
        }
    }

    private record District(UUID id, String name) {
    }
}
