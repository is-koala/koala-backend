package com.exskylab.koala.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateBoundaryTest {

    private static final Path MANAGERS = Paths.get("src/main/java/com/exskylab/koala/business/concretes");
    private static final Pattern DAO_TYPE = Pattern.compile("\\b([A-Z]\\w+(?:Dao|Repository))\\b");
    private static final Pattern ENTITY_MANAGER_CALL =
            Pattern.compile("entityManager\\.(\\w+)\\s*\\(");
    private static final Set<String> ENTITY_MANAGER_LOCAL_ONLY = Set.of("flush", "clear");

    private static final Map<String, Set<String>> OWNED_BY = Map.ofEntries(
            Map.entry("GalleryManager", Set.of("CompanyImageDao", "JobImageDao")),
            Map.entry("MessagingManager", Set.of("ConversationDao", "MessageDao", "UserBlockDao")),
            Map.entry("AvailabilityManager", Set.of("AvailabilitySlotDao", "AvailabilityExceptionDao")),
            Map.entry("BadgeManager", Set.of("BadgeDao", "UserBadgeDao")),
            Map.entry("LegalManager", Set.of("AgreementVersionDao", "ConsentRecordDao", "AccountDeletionRequestDao")),
            Map.entry("LocationManager", Set.of("CountryDao", "CityDao", "DistrictDao")),
            Map.entry("AcademicReferenceManager", Set.of("UniversityDao", "FacultyDao", "DepartmentDao")),
            Map.entry("ReviewManager", Set.of("ReviewDao", "RatingSummaryDao")),
            Map.entry("PayoutManager", Set.of("PayoutDao", "RefundDao")),
            Map.entry("TimesheetManager", Set.of("WorkSessionDao")),
            Map.entry("CvManager", Set.of("MediaDao")),
            Map.entry("DisputeManager", Set.of("DisputeDao", "DisputeEvidenceDao")),
            Map.entry("StaffAuthorizationManager", Set.of("StaffRoleDao",
                    "StaffRolePermissionDao", "StaffRoleIncludeDao")),
            Map.entry("StaffAccountManager", Set.of("StaffMemberDao", "StaffMemberRoleDao")),
            Map.entry("StaffAuditManager", Set.of("AdminAuditEntryDao", "AdminAuditEntryDetailDao")),
            Map.entry("NotificationPreferenceManager",
                    Set.of("NotificationPreferenceDao", "CommercialMessageConsentDao")),
            Map.entry("NotificationDispatchManager", Set.of("NotificationDao")),
            Map.entry("UserProfileSyncManager", Set.of("UserProfileSyncOutboxDao")),
            Map.entry("IysManager", Set.of("IysConsentTaskDao", "IysPermissionStateDao")));

    private static final Map<String, Set<String>> RENAMED_AGGREGATES = Map.of(
            "TalentPoolManager", Set.of("TalentPoolEntryDao"),
            "SettlementManager", Set.of("SettlementTaskDao"),
            "SettingsManager", Set.of("AppSettingDao"),
            "SavedJobManager", Set.of(),
            "SecurityManager", Set.of(),
            "PushDeliveryStore", Set.of("NotificationDao"));

    private record Exemption(String manager, String dao, String reason) {
    }

    private static final List<Exemption> EXEMPTIONS = List.of(
            new Exemption("SecurityManager", "UserDao",
                    "altyapı sınıfı: UserService zaten SecurityService'e bağlı, ters yön döngü olurdu"),
            new Exemption("BadgeManager", "UserDao",
                    "salt okuma; UserManager BadgeService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("BadgeManager", "JobAssignmentDao",
                    "salt okuma; JobAssignmentManager BadgeService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("BadgeManager", "RatingSummaryDao",
                    "salt okuma; ReviewManager BadgeService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("JobAssignmentManager", "CancellationDao",
                    "salt okuma; atama yanıtı iptal özetini taşır ve CancellationManager JobAssignmentService'e bağlı "
                            + "olduğu için ters yön döngü olurdu"),
            new Exemption("JobAssignmentManager", "ReviewDao",
                    "salt okuma; atama yanıtı myReview/counterpartyReviewed taşır ve ReviewManager "
                            + "JobAssignmentService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("PayoutManager", "JobAssignmentDao",
                    "salt okuma; JobAssignmentManager PayoutService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("PayoutManager", "DisputeDao",
                    "salt okuma; DisputeManager StaffPayoutService üzerinden PayoutService'e bağlı olduğu için "
                            + "ters yön döngü olurdu"),
            new Exemption("JobManager", "JobAssignmentDao",
                    "salt okuma; JobManager MessagingService uzerinden JobAssignmentService'e baglansa dongu olurdu"),
            new Exemption("JobManager", "JobApplicationDao",
                    "salt okuma; JobApplicationManager JobService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("SettlementManager", "PayoutDao",
                    "mutabakat görevi bir payout için var; PayoutManager SettlementService'e bağlı, ters yön döngü olurdu"),
            new Exemption("SettlementManager", "RefundDao",
                    "sağlayıcı yanıtı iadeyi sonlandırır; PayoutManager SettlementService'e bağlı, ters yön döngü olurdu"),
            new Exemption("PaymentManager", "JobAssignmentDao",
                    "salt okuma sahiplik kapisi; JobAssignmentManager PaymentService'e bagli oldugu icin ters yon dongu olurdu"),
            new Exemption("JobCategoryManager", "JobDao",
                    "salt okuma silme kapısı; JobManager JobCategoryService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("JobCategoryManager", "JobAlertDao",
                    "salt okuma silme kapısı; JobAlertManager JobCategoryService'e bağlı olduğu için ters yön döngü olurdu"),
            new Exemption("JobCategoryManager", "JobTemplateDao",
                    "salt okuma silme kapısı; JobTemplateManager JobService üzerinden JobCategoryService'e bağlı, ters yön döngü olurdu"),
            new Exemption("StaffAuthorizationManager", "StaffMemberRoleDao",
                    "salt okuma görev ayrılığı kapısı: bir kenar eklenirken o kenardan etkilenen personellerin rol "
                            + "birleşimi de doğrulanıyor; StaffAccountManager StaffAuthorizationService'e bağlı "
                            + "olduğu için ters yön döngü olurdu"));

    private static Set<String> ownDaosOf(String manager) {
        Set<String> owned = OWNED_BY.get(manager);
        if (owned != null) {
            return owned;
        }
        owned = RENAMED_AGGREGATES.get(manager);
        return owned != null ? owned : Set.of(manager.replace("Manager", "") + "Dao");
    }

    private static Map<String, Set<String>> foreignDaosByManager() throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();

        try (Stream<Path> files = Files.list(MANAGERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String manager = file.getFileName().toString().replace(".java", "");
                Set<String> used = new TreeSet<>();

                Matcher matcher = DAO_TYPE.matcher(Files.readString(file));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }

                used.removeAll(ownDaosOf(manager));
                if (!used.isEmpty()) {
                    found.put(manager, used);
                }
            }
        }
        return found;
    }

    @Test
    void aManagerOnlyReachesForItsOwnAggregatesRepository() throws IOException {
        Set<String> allowed = new HashSet<>();
        EXEMPTIONS.forEach(e -> allowed.add(e.manager() + "#" + e.dao()));

        List<String> failures = new ArrayList<>();

        foreignDaosByManager().forEach((manager, daos) -> daos.stream()
                .filter(dao -> !allowed.contains(manager + "#" + dao))
                .forEach(dao -> failures.add(
                        ("%s kendi olmayan %s'yu kullanıyor. Diğer aggregate'e erişim onun Service'i üzerinden olmalı; "
                                + "servis üzerinden gitmek bean döngüsü yaratıyorsa EXEMPTIONS listesine gerekçesiyle eklenmeli.")
                                .formatted(manager, dao))));

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everyExemptionIsStillNeeded() throws IOException {
        Map<String, Set<String>> foreign = foreignDaosByManager();

        List<String> stale = EXEMPTIONS.stream()
                .filter(e -> !foreign.getOrDefault(e.manager(), Set.of()).contains(e.dao()))
                .map(e -> e.manager() + "#" + e.dao())
                .toList();

        assertTrue(stale.isEmpty(),
                "artık gerekmeyen muafiyet duruyor, listeden çıkarılmalı: " + stale);
    }

    @Test
    void everyExemptionCarriesAWrittenReason() {
        List<String> silent = EXEMPTIONS.stream()
                .filter(e -> e.reason() == null || e.reason().isBlank())
                .map(Exemption::manager)
                .toList();

        assertTrue(silent.isEmpty(), "gerekçesiz muafiyet: " + silent);
    }

    @Test
    void aRepositorySuffixIsStillABoundaryCrossing() {
        Set<String> used = dataAccessTypesIn("private final UserRepository userRepository;");
        assertTrue(used.contains("UserRepository"),
                "Dao ile bitmeyen veri erisim tipi siniri sessizce gecti: " + used);
    }

    @Test
    void entityManagerPersistCrossesTheAggregateBoundary() {
        assertTrue(entityManagerCrossesAggregate("entityManager.persist(user);"),
                "persist taramada yoktu, koruma EntityManager yolunu gormuyordu.");
    }

    @Test
    void flushAndClearAreNotBoundaryCrossings() {
        assertTrue(!entityManagerCrossesAggregate("entityManager.flush();\nentityManager.clear();"),
                "flush/clear yerel kalicilik araci, yabanci aggregate degil.");
    }

    @Test
    void managersDoNotUseEntityManagerToReachAnotherAggregate() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(MANAGERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (entityManagerCrossesAggregate(source)) {
                    failures.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "EntityManager yabanci aggregate'e uzaniyor: " + failures);
    }

    static Set<String> dataAccessTypesIn(String source) {
        Set<String> used = new TreeSet<>();
        Matcher matcher = DAO_TYPE.matcher(source);
        while (matcher.find()) {
            used.add(matcher.group(1));
        }
        return used;
    }

    static boolean entityManagerCrossesAggregate(String source) {
        Matcher matcher = ENTITY_MANAGER_CALL.matcher(source);
        while (matcher.find()) {
            if (!ENTITY_MANAGER_LOCAL_ONLY.contains(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }
}
