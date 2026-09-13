package com.exskylab.koala.dataAccess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EagerFetchCoverageIT} yalnız zaten {@code @EntityGraph} taşıyan
 * metotlara bakıyordu. Grafı olmayan koleksiyon sorgusu N+1'in asıl kaynağı
 * olduğu halde taramaya hiç girmiyordu. Bu test o kör noktayı kapatır: yeni
 * grafsız koleksiyon sorgusu allowlist'e yazılmadan eklenemez.
 */
class CollectionQueryWithoutGraphTest {

    private static final Path DAOS = Path.of("src/main/java/com/exskylab/koala/dataAccess");

    private static final Pattern COLLECTION_METHOD = Pattern.compile(
            "^\\s+(?:public\\s+|default\\s+)?(?:[\\w.<>,\\s]+?\\s+)?(?:List|Page|Slice|org\\.springframework\\.data\\.domain\\.Page)<[^>]+>\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    /**
     * Grafı olmayan koleksiyon sorguları. Çoğu kimlik listesi veya referans
     * tablosu; EntityGraph eklemek N+1 üretmez. Yeni bir satır eklerken
     * gerekçe yorumu yaz.
     */
    private static final Set<String> GRAPHLESS_COLLECTION_QUERIES = Set.of(
            "AccountDeletionRequestDao.findAllByStatus",
            "AccountDeletionRequestDao.findAll",
            "AccountDeletionRequestDao.findDueIds",
            "AdminAuditEntryDao.search",
            "AgreementVersionDao.findAllCurrent",
            "AppSettingDao.findAllByOrderByKeyAsc",
            "AvailabilityExceptionDao.findAllByUserIdAndDateGreaterThanEqualOrderByDateAsc",
            "AvailabilitySlotDao.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc",
            "BankAccountDao.findAllByUserIdAndActiveTrue",
            "BankAccountDao.findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc",
            "CityDao.findCitiesByCountryIdOrderByNameAsc",
            "CommercialMessageConsentDao.findAllByUserIdOrderByRecordedAtDesc",
            "CompanyContactInvitationDao.findByCompany",
            "CompanyContactInvitationDao.findByCompanyId",
            "CompanyDao.search",
            "CountryDao.findCountriesByOrderByNameAsc",
            "DepartmentDao.findAllByFacultyIdOrderByNameAsc",
            "DeviceDao.findAllByUser_IdAndPushTokenIsNotNull",
            "DeviceDao.findAllByPushToken",
            "DistrictDao.findDistrictsByCityIdOrderByNameAsc",
            "ExperienceDao.findAllByUserIdOrderByStartDateDesc",
            "FacultyDao.findAllByUniversityIdOrderByNameAsc",
            "IysConsentTaskDao.findDueIds",
            "IysPermissionStateDao.findByNextReconciliationAtLessThanEqualOrderByNextReconciliationAtAsc",
            "JobApplicationDao.countByJobIds",
            "JobApplicationDao.findCompanyIdsAppliedToBy",
            "JobAssignmentDao.findVisibleAddressJobIds",
            // Conversation list resolves its DIRECT (job, worker) pairs in one batch; the page already loaded
            // those jobs and workers, so the eager to-ones come from the persistence context (#257).
            "JobAssignmentDao.findAllByJobIdInAndWorkerIdIn",
            "JobAssignmentDao.findAutoCompletableIds",
            "JobAssignmentDao.findUnpaidExpiringIds",
            "JobAssignmentDao.countHiredByJobIds",
            "JobDao.searchIds",
            "MessageDao.countUnreadByConversationIds",
            "NotificationDao.findAllByRecipientIdOrderByCreatedAtDesc",
            "NotificationDao.findAllByRecipientIdAndChannelOrderByCreatedAtDesc",
            "NotificationDao.findDuePushDeliveryIds",
            "NotificationDao.findStalePushDeliveryIds",
            "NotificationDao.findDueDispatchIds",
            "NotificationPreferenceDao.findAllByUserId",
            "PaymentDao.findDueVerificationIds",
            "PaymentDao.findStuckInitiationIds",
            // Aggregate SUM/COUNT row, not an entity collection (#186).
            "PaymentDao.summarizeSucceeded",
            "PayoutDao.findIdsAwaitingSettlementSince",
            // Aggregate SUM/COUNT rows, not entity collections (#186).
            "PayoutDao.summarizeAwaiting",
            "PayoutDao.summarizeCompleted",
            "RefundDao.findAllByPaymentIdOrderByCreatedAtDesc",
            "ReportDao.findAllByReporterId",
            "ReportDao.search",
            "ReportDao.tallyByReportedUser",
            "SettlementTaskDao.findDueIds",
            "SitemapManifestDao.readShard",
            "StaffMemberDao.search",
            "StaffRoleDao.findAllByCodeIn",
            "StaffRoleIncludeDao.findAllByParentRoleIdIn",
            "StaffRolePermissionDao.findAllByRoleIdIn",
            "UniversityDao.findAllByOrderByNameAsc",
            "UserDao.findByEmailVerifiedTrue",
            "UserDao.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase",
            "UserDao.search",
            "UserDao.findRoleGrants",
            // Native id list for the Iyzico profile-sync dispatch worker (#158); no entity graph.
            "UserProfileSyncOutboxDao.findDueDispatchIds",
            "WorkSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc");

    @Test
    void aCollectionQueryWithoutEntityGraphIsReported() {
        String source = """
                public interface ExampleDao {
                    List<User> findAllByActiveTrue();
                }
                """;

        assertTrue(graphlessQueriesIn("ExampleDao", source).contains("ExampleDao.findAllByActiveTrue"),
                "grafi olmayan koleksiyon sorgusu taramada yoktu");
    }

    @Test
    void collectionQueriesWithoutEntityGraphAreDocumented() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(DAOS)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Dao.java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                found.addAll(graphlessQueriesIn(name, Files.readString(file, StandardCharsets.UTF_8)));
            }
        }

        List<String> undocumented = found.stream()
                .filter(key -> !GRAPHLESS_COLLECTION_QUERIES.contains(key))
                .toList();
        List<String> stale = GRAPHLESS_COLLECTION_QUERIES.stream()
                .filter(key -> !found.contains(key))
                .sorted()
                .toList();

        assertTrue(undocumented.isEmpty(),
                "Grafi olmayan yeni koleksiyon sorgusu. @EntityGraph ekle ya da allowlist'e gerekcesiyle yaz: "
                        + undocumented);
        assertTrue(stale.isEmpty(),
                "Allowlist bayatladi, metot yok veya graf kazandi: " + stale);
    }

    static Set<String> graphlessQueriesIn(String dao, String source) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = COLLECTION_METHOD.matcher(source);
        while (matcher.find()) {
            String preceding = source.substring(Math.max(0, matcher.start() - 400), matcher.start());
            if (preceding.contains("@EntityGraph")) {
                continue;
            }
            found.add(dao + "." + matcher.group(1));
        }
        return found;
    }
}
