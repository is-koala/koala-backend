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
            "AccountDeletionRequestDao.findDueIds",
            // AccountDeletionDto renders no user; the search joins users only to match the e-mail (#254).
            "AccountDeletionRequestDao.search",
            "AdminAuditEntryDao.search",
            "AgreementVersionDao.findAllCurrent",
            "AppSettingDao.findAllByOrderByKeyAsc",
            "AvailabilityExceptionDao.findAllByUserIdAndDateGreaterThanEqualOrderByDateAsc",
            "AvailabilitySlotDao.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc",
            "BankAccountDao.findAllByUserIdAndActiveTrue",
            "BankAccountDao.findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc",
            // MyAssignmentDto renders the cancellation row and jobAssignment.id (proxy identifier, no query); the
            // assignments themselves are already in the persistence context (#282).
            "CancellationDao.findAllByJobAssignmentIdIn",
            "CityDao.findCitiesByCountryIdOrderByNameAsc",
            "CommercialMessageConsentDao.findAllByUserIdOrderByRecordedAtDesc",
            "CompanyContactInvitationDao.findByCompany",
            "CompanyContactInvitationDao.findByCompanyId",
            "CompanyDao.search",
            // Native read model for ConversationDto.participants: one query per page, includes soft-deleted
            // (left) participants the entity's @SQLRestriction hides, joins users/media itself (#281).
            "ConversationDao.findParticipantRows",
            // Deletion blockers: id + party projection rows, not entities (#283).
            "DisputeDao.findActiveLinksForUsers",
            // Staff payment list reads only status, resolution, id and jobAssignment.id (proxy identifier, no
            // query) to attribute a refund to a dispute; nothing of the dispute is drawn (#301).
            "DisputeDao.findAllByJobAssignmentIdInOrderByCreatedAtDesc",
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
            // Same lookup for one (job, worker) pair: one or two rows, reduced to the latest in Java (#257).
            "JobAssignmentDao.findAllByJobIdAndWorkerId",
            "JobAssignmentDao.findAutoCompletableIds",
            "JobAssignmentDao.findUnpaidExpiringIds",
            "JobAssignmentDao.countHiredByJobIds",
            // Deletion blockers: id + party projection rows, not entities (#283).
            "JobAssignmentDao.findActiveLinksForUsers",
            "JobAssignmentDao.findMoneyLinksForUsers",
            // Staff payment list: id/title/party-id projection rows per page, not entities; keeps a deleted
            // worker's payments on the list where an entity load would INNER-join them away (#301, §2y3).
            "JobAssignmentDao.findStaffSummariesByIdIn",
            // Aggregate COUNT rows per company for CompanyReviewDto.jobCount (#283).
            "JobDao.countByCompanyIds",
            "JobDao.searchIds",
            // LoginHistoryEntryDto renders only scalar columns (time, ip, location, success); user and device
            // are lazy proxies the mapper never touches (#306).
            "LoginAttemptDao.findAllByUser_IdAndDevice_IdOrderByTimeStampDesc",
            "MessageDao.countUnreadByConversationIds",
            // Message pages fetch no sender: with Message.sender nullable=false and User @SQLRestriction the graph
            // became an INNER join that dropped deleted senders' messages (#231 trap). The page reads only
            // sender.id (proxy, no query); names and pictures come from ConversationDao.findParticipantRows,
            // one query per page (#281).
            "MessageDao.findAllByConversationIdOrderBySentAtDesc",
            "MessageDao.findAllAfter",
            "NotificationDao.findAllByRecipientIdOrderByCreatedAtDesc",
            "NotificationDao.findAllByRecipientIdAndChannelOrderByCreatedAtDesc",
            "NotificationDao.findDuePushDeliveryIds",
            "NotificationDao.findStalePushDeliveryIds",
            "NotificationDao.findDueDispatchIds",
            "NotificationPreferenceDao.findAllByUserId",
            "PaymentDao.findDueVerificationIds",
            // Aggregate MIN row per assignment, not an entity collection (#282).
            "PaymentDao.findSecuredAtByJobAssignmentIds",
            // Deletion blockers: id + party projection rows, not entities (#283).
            "PaymentDao.findPendingLinksForUsers",
            "PaymentDao.findStuckInitiationIds",
            // Staff payment list deliberately fetches no graph: Payment.jobAssignment and JobAssignment.worker are
            // nullable=false under @SQLRestriction and a graph would INNER-join a deleted worker's payments away
            // (§2y3). The page reads only jobAssignment.id (proxy, no query); assignment summaries and people come
            // from two batch queries per page (#301).
            "PaymentDao.searchForStaff",
            // Aggregate SUM/COUNT row, not an entity collection (#186).
            "PaymentDao.summarizeSucceeded",
            // MyAssignmentDto renders id, status and completedAt of the payout row plus jobAssignment.id (proxy
            // identifier, no query); the assignments are already in the persistence context (#282).
            "PayoutDao.findAllByJobAssignmentIdIn",
            "PayoutDao.findIdsAwaitingSettlementSince",
            // Aggregate SUM/COUNT rows, not entity collections (#186).
            "PayoutDao.summarizeAwaiting",
            "PayoutDao.summarizeCompleted",
            "RefundDao.findAllByPaymentIdOrderByCreatedAtDesc",
            // MyAssignmentDto renders myReview/counterpartyReviewed from author.id and jobAssignment.id (proxy
            // identifiers, no query); the assignments are already in the persistence context (#293).
            "ReviewDao.findAllByJobAssignmentIdIn",
            // Staff payment detail reads only the refund row and payment.id (proxy identifier, no query); the
            // payments themselves are already in the persistence context (#255).
            "RefundDao.findAllByPaymentIdInOrderByCreatedAtDesc",
            // Staff assignment detail renders reports as id + status only; no reporter or job is drawn (#255).
            "ReportDao.findAllBetweenAssignmentParties",
            "ReportDao.findAllByReporterId",
            "ReportDao.tallyByReportedUser",
            // Staff assignment detail renders review rows with author/subject ids only (proxy identifiers) (#283).
            "ReviewDao.findAllByJobAssignmentIdOrderByCreatedAtAsc",
            "SettlementTaskDao.findDueIds",
            // Seat rows for a page of assignments: MyAssignmentDto.transport reads shuttle.id/stop.id/jobAssignment.id
            // (proxy identifiers, no query); shuttles and stops are batch-loaded separately (#297).
            "SeatAssignmentDao.findAllByJobAssignmentIdIn",
            // Plan rewrite drops the seats of removed shuttles/stops; only the rows are deleted, nothing is drawn (#297).
            "SeatAssignmentDao.findAllByShuttleIdIn",
            "SeatAssignmentDao.findAllByStopIdIn",
            // Aggregate COUNT rows per shuttle/stop for seatsTaken (#297).
            "SeatAssignmentDao.countByShuttleIds",
            "SeatAssignmentDao.countByStopIds",
            // Reminder scheduler id lists; each seat is then loaded with its own graph (#297).
            "SeatAssignmentDao.findEveningReminderDueIds",
            "SeatAssignmentDao.findDepartureReminderDueIds",
            "SeatAssignmentDao.findBoardingReminderDueIds",
            // Departures (stop ids) due for the aggregated not-boarded notice; each is then handled on its own (#298).
            "SeatAssignmentDao.findNotBoardedDueStopIds",
            // The worker's seats on one shuttle (one per assignment, in practice one); only the assignment status is
            // read before the chosen row is reloaded with its own graph (#298).
            "SeatAssignmentDao.findAllByShuttleIdAndJobAssignment_Worker_Id",
            // Boarding events of a page of seats; the mapper reads seatAssignment/jobAssignment/shuttle/recordedBy ids
            // only (proxy identifiers, no query) (#298).
            "BoardingEventDao.findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc",
            // One seat's events on one departure day for the idempotency decision; ids only (#298).
            "BoardingEventDao.findAllBySeatAssignmentIdAndBoardingDayOrderByCreatedAtAscIdAsc",
            // Staff timeline boarding rows of one assignment; ids only, no relation is drawn (#298).
            "BoardingEventDao.findAllByJobAssignmentIdOrderByOccurredAtAscIdAsc",
            // Job ids whose plan uses a shuttle, for the JobSummaryDto.hasShuttle badge (#297).
            "TransportPlanDao.findJobIdsWithMethod",
            // Staff payout detail renders the task row only; payment/jobAssignment proxies are never touched (#255).
            "SettlementTaskDao.findAllByPaymentIdOrderByCreatedAtAsc",
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
