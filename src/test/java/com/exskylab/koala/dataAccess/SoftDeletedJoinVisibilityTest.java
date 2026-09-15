package com.exskylab.koala.dataAccess;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every entity is soft-deleted and carries {@code @SQLRestriction("is_deleted = false")}. Hibernate 7 writes that
 * restriction into the ON clause of a fetch join, and a to-one association declared {@code optional = false} /
 * {@code nullable = false} is fetched with an INNER join — so when the related row is soft-deleted the owning row
 * vanishes from the result. It happened three times before this scan existed: the payout queue lost a PROCESSING
 * payout whose worker had deleted their account (§2y3), the message page lost a deleted sender's messages (§2o), and
 * {@code /admin/payments} lost a deleted worker's SUCCEEDED payments (§2s7, #301). Each was found by hand (#309).
 * <p>
 * The scan walks every {@code @EntityGraph} path and every {@code join fetch} in a DAO and flags the hop when all
 * three hold: the association is mandatory, the target is soft-deleted, and the codebase has a path that actually
 * soft-deletes the target (a DAO {@code delete*} call, an anonymizer statement, or {@code orphanRemoval}). Entities
 * nothing deletes today (Job, JobAssignment, Payment, Dispute, Report, Conversation, ...) are not flagged; the day
 * someone adds a delete path for them, every dependent fetch path surfaces here.
 * <p>
 * Staff list and read endpoints must not lose rows this way: the fix is the batch second query per page
 * ({@code UserService.getAllByIdIn}, {@code JobAssignmentDao.findStaffSummariesByIdIn}). Participant endpoints are
 * listed for a separate evaluation.
 */
class SoftDeletedJoinVisibilityTest {

    private static final Path MAIN = Path.of("src/main/java/com/exskylab/koala");
    private static final Path DAOS = MAIN.resolve("dataAccess");
    private static final Path ENTITIES = MAIN.resolve("entities");
    private static final Path ANONYMIZER = MAIN.resolve("core/privacy/PostgresAccountAnonymizer.java");

    private static final String DAO_PACKAGE = "com.exskylab.koala.dataAccess.";
    private static final String ENTITY_PACKAGE = "com.exskylab.koala.entities.";

    private static final Pattern STAFF_METHOD = Pattern.compile("(?i)staff|search|moderation");

    private static final Pattern DAO_FIELD = Pattern.compile("\\b(\\w+Dao)\\s+(\\w+)\\s*[;,=)]");
    private static final Pattern DELETE_CALL = Pattern.compile("\\b(\\w+)\\.delete\\w*\\(");
    private static final Pattern SOFT_DELETE_STATEMENT = Pattern.compile(
            "update\\s+(\\w+)\\s+set[^;\"]*?is_deleted\\s*=\\s*true", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JOIN_FETCH = Pattern.compile("(left\\s+)?join\\s+fetch\\s+\\w+\\.([\\w.]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * DAO methods that admin endpoints reach although the name does not say so. Key: DAO.method; value: the
     * controller chain.
     */
    private static final Map<String, String> STAFF_READ_METHODS = Map.ofEntries(
            Map.entry("CancellationDao.findWithCancellerByJobAssignmentId",
                    "AdminController GET /admin/job-assignments/{id} -> AdminDetailManager.getAssignmentDetail"),
            Map.entry("CompanyContactDao.findAllByCompanyId",
                    "AdminController GET /admin/companies/{id} -> AdminDetailManager.getCompany"),
            Map.entry("CompanyContactDao.findAllByCompanyIdIn",
                    "AdminController GET /admin/companies -> AdminDetailManager.searchCompanies"),
            Map.entry("DisputeDao.findAllByJobAssignmentIdOrderByCreatedAtDesc",
                    "AdminController GET /admin/job-assignments/{id} -> DisputeManager.findAllForAssignment"),
            Map.entry("DisputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc",
                    "DisputesController GET /admin/disputes/{id}/evidence -> DisputeManager.getEvidenceForStaff"),
            Map.entry("IdentityRevealDao.findAllByWorker_Id",
                    "AdminController GET /admin/users/{id}/identity-reveals -> IdentityRevealManager.listForStaff"),
            Map.entry("JobAssignmentDao.findWithDetailsForPaymentById",
                    "AdminController GET /admin/job-assignments/{id} -> JobAssignmentManager.getDetailedById"),
            Map.entry("JobDao.findWithDetailsById",
                    "AdminController GET /admin/jobs/{id} -> JobManager.getAdminDetail"),
            Map.entry("ReportDao.findDetailedById",
                    "ReportsController GET /admin/reports/{id} -> ReportManager.getForModeration"));

    /**
     * Staff fetch paths that may keep the INNER join. Only two reasons are accepted: the related row is deleted
     * together with the owning row (cascade, nothing is orphaned), or the fix is tracked in an issue. Key:
     * DAO.method#path.
     */
    private static final Map<String, String> STAFF_ALLOWLIST = Map.ofEntries(
            // PostgresAccountAnonymizer soft-deletes the user's bank_accounts with the user; no orphan row exists.
            Map.entry("BankAccountDao.findAwaitingStaffConfirmation#user", "cascade: anonymizer deletes bank_accounts"),
            Map.entry("BankAccountDao.findAwaitingStaffConfirmation#user.profilePicture",
                    "cascade: anonymizer deletes bank_accounts"),
            // PostgresAccountAnonymizer soft-deletes dispute_evidence uploaded by the user.
            Map.entry("DisputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc#uploadedBy",
                    "cascade: anonymizer deletes dispute_evidence of the uploader"),
            Map.entry("DisputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc#uploadedBy.profilePicture",
                    "cascade: anonymizer deletes dispute_evidence of the uploader"),
            // Staff single reads whose composition still carries the worker (§2s7 #301: "tekil okuma, kural
            // değişmedi") and the assignment detail's cancellation/dispute rows; tracked as follow-ups of #309.
            Map.entry("CancellationDao.findWithCancellerByJobAssignmentId#cancelledBy", "follow-up of #309"),
            Map.entry("CancellationDao.findWithCancellerByJobAssignmentId#cancelledBy.profilePicture",
                    "follow-up of #309"),
            Map.entry("DisputeDao.findAllByJobAssignmentIdOrderByCreatedAtDesc#openedBy", "follow-up of #309"),
            Map.entry("DisputeDao.findAllByJobAssignmentIdOrderByCreatedAtDesc#openedBy.profilePicture",
                    "follow-up of #309"),
            Map.entry("DisputeDao.searchForStaff#openedBy", "follow-up of #309"),
            Map.entry("DisputeDao.searchForStaff#openedBy.profilePicture", "follow-up of #309"),
            Map.entry("DisputeDao.findByIdForStaff#openedBy", "follow-up of #309"),
            Map.entry("DisputeDao.findByIdForStaff#openedBy.profilePicture", "follow-up of #309"),
            Map.entry("JobAssignmentDao.findWithDetailsForPaymentById#worker", "follow-up of #309"),
            Map.entry("JobAssignmentDao.findWithDetailsForPaymentById#worker.profilePicture", "follow-up of #309"),
            Map.entry("PaymentDao.findForStaffById#jobAssignment.worker", "follow-up of #309"),
            // Company contacts of a deleted user stay in company_contacts; the staff company detail drops them.
            Map.entry("CompanyContactDao.findAllByCompanyId#user", "follow-up of #309"),
            Map.entry("CompanyContactDao.findAllByCompanyId#user.profilePicture", "follow-up of #309"),
            Map.entry("CompanyContactDao.findAllByCompanyIdIn#user", "follow-up of #309"),
            Map.entry("CompanyContactDao.findAllByCompanyIdIn#user.profilePicture", "follow-up of #309"));

    /**
     * Participant and owner fetch paths through a mandatory soft-deleted relation. Whether the row must survive
     * the counterpart's deletion (a "Silinmiş kullanıcı" rendering) is decided per endpoint in the follow-up of
     * #309; until then every path is listed so that a new one cannot appear unnoticed. Key: DAO.method#path.
     */
    private static final Set<String> PARTICIPANT_PATHS_AWAITING_EVALUATION = Set.of(
            // Owner reads: the row is the user's own and is soft-deleted with the user by the anonymizer.
            "AvailabilityExceptionDao.findWithUserById#user",
            "AvailabilitySlotDao.findWithUserById#user",
            "EducationDao.findWithDetailsById#user",
            "ExperienceDao.findWithUserById#user",
            "IysConsentTaskDao.findTopByUserIdAndChannelOrderByDecisionSequenceDesc#user",
            "JobAlertDao.findWithDetailsById#user",
            "JobAlertDao.findAllByActiveTrue#user",
            "JobTemplateDao.findWithDetailsById#owner",
            "TalentPoolEntryDao.findAllByEmployerId#worker",
            "TalentPoolEntryDao.findAllByEmployerId#worker.profilePicture",
            "UnsubscribeTokenDao.findByTokenDigest#user",
            // Gallery rows are deleted with their image (GalleryManager.removeImageEverywhere).
            "CompanyImageDao.findAllByCompanyIdOrderBySortOrderAsc#image",
            "CompanyImageDao.findWithDetailsById#image",
            "CompanyImageDao.findByCompanyIdAndImageId#image",
            "JobImageDao.findAllByJobIdOrderBySortOrderAsc#image",
            "JobImageDao.findAllByJobIdInAndCoverTrue#image",
            "JobImageDao.findWithDetailsById#image",
            // Transport plan rows own their stops and addresses (orphanRemoval); the seat is the worker's own.
            "SeatAssignmentDao.findWithDetailsById#jobAssignment.worker",
            "SeatAssignmentDao.findWithDetailsById#shuttle",
            "SeatAssignmentDao.findWithDetailsById#stop",
            "SeatAssignmentDao.findWithDetailsById#stop.address",
            "SeatAssignmentDao.findWithDetailsById#stop.address.district",
            "ShuttleStopDao.findAllByShuttleIdInOrderBySortOrderAsc#address",
            "ShuttleStopDao.findAllByShuttleIdInOrderBySortOrderAsc#address.city",
            "ShuttleStopDao.findAllByShuttleIdInOrderBySortOrderAsc#address.city.country",
            "ShuttleStopDao.findAllByShuttleIdInOrderBySortOrderAsc#address.district",
            // Counterpart may have deleted their account: the participant's own list loses the row.
            "CompanyFollowDao.findAllByCompanyId#follower",
            "ConversationDao.findWithParticipantsById#employer",
            "ConversationDao.findWithParticipantsById#employer.profilePicture",
            "ConversationDao.findAllForParticipant#employer",
            "ConversationDao.findAllForParticipant#employer.profilePicture",
            "JobApplicationDao.findAllByJobId#user",
            "JobApplicationDao.findAllByJobId#user.profilePicture",
            "JobApplicationDao.findAllByJobId#user.cv",
            "JobApplicationDao.findReceivedPendingFirst#user",
            "JobApplicationDao.findReceivedPendingFirst#user.profilePicture",
            "JobApplicationDao.findReceived#user",
            "JobApplicationDao.findReceived#user.profilePicture",
            "JobAssignmentDao.findAllByWorkerId#worker",
            "JobAssignmentDao.findAllByWorkerId#worker.profilePicture",
            "JobAssignmentDao.findAllByJobCreatorId#worker",
            "JobAssignmentDao.findAllByJobCreatorId#worker.profilePicture",
            "JobAssignmentDao.findAllByJobId#worker",
            "JobAssignmentDao.findAllByJobId#worker.profilePicture",
            "JobAssignmentDao.findAllByJobIdAndStatusIn#worker",
            "MessageDao.findReportableByIdForUpdate#sender",
            "PaymentDao.findByProviderToken#jobAssignment.worker",
            "PaymentDao.findWithDetailsById#jobAssignment.worker",
            "PaymentDao.findByConversationId#jobAssignment.worker",
            "PayoutDao.findAllByWorkerIdOrderByCreatedAtDesc#worker",
            "ReviewDao.findAllBySubjectIdOrderByCreatedAtDesc#author",
            "ReviewDao.findAllBySubjectIdOrderByCreatedAtDesc#author.profilePicture",
            "UserBlockDao.findAllByBlockerId#blocked",
            "UserBlockDao.findAllByBlockerId#blocked.profilePicture");

    private record FetchPath(String dao, String method, String path, List<String> droppedBy) {

        String key() {
            return dao + "." + method + "#" + path;
        }

        boolean staff() {
            return STAFF_METHOD.matcher(method).find() || STAFF_READ_METHODS.containsKey(dao + "." + method);
        }
    }

    @Test
    void theScanRecognisesTheThreeDocumentedTraps() throws Exception {
        Set<String> deletable = deletableEntities();

        assertTrue(droppedBy(entity("Payout"), "worker", deletable).contains("User"),
                "Payout.worker (§2y3) should be recognised as an INNER join on a deletable user");
        assertTrue(droppedBy(entity("Message"), "sender", deletable).contains("User"),
                "Message.sender (§2o) should be recognised as an INNER join on a deletable user");
        assertTrue(droppedBy(entity("Payment"), "jobAssignment.worker", deletable).contains("User"),
                "Payment.jobAssignment.worker (§2s7 #301) should be recognised through the assignment");
        assertTrue(droppedBy(entity("Job"), "creator", deletable).isEmpty(),
                "Job.creator is nullable: a LEFT join, the job stays with a null creator");
        assertTrue(droppedBy(entity("Payment"), "jobAssignment", deletable).isEmpty(),
                "nothing deletes a JobAssignment today; the hop is mandatory but no orphan can exist");
    }

    @Test
    void theDeletionEvidenceFindsTheKnownDeletePaths() throws Exception {
        Set<String> deletable = deletableEntities();

        for (String expected : List.of("User", "Media", "BankAccount", "DisputeEvidence", "ConversationParticipant",
                "CompanyContact", "Education", "Address")) {
            assertTrue(deletable.contains(expected), expected + " has a delete path but the scan missed it: "
                    + deletable);
        }
        for (String neverDeleted : List.of("Job", "JobAssignment", "Payment", "Payout", "Dispute", "Report",
                "Conversation", "Message", "Review")) {
            assertFalse(deletable.contains(neverDeleted), neverDeleted + " gained a delete path. Every fetch "
                    + "path through it is now a visibility risk; run this test and list them.");
        }
    }

    @Test
    void staffFetchPathsThroughAMandatorySoftDeletedRelationAreFixedOrJustified() throws Exception {
        List<FetchPath> risky = riskyFetchPaths();

        List<String> violations = risky.stream()
                .filter(FetchPath::staff)
                .filter(path -> !STAFF_ALLOWLIST.containsKey(path.key()))
                .map(path -> path.key() + " drops the row when its " + path.droppedBy() + " is soft-deleted")
                .sorted()
                .toList();
        assertTrue(violations.isEmpty(), "Staff list/read fetch path through a mandatory soft-deleted relation. "
                + "Fetch the row without it and load the related rows with one batch query per page "
                + "(UserService.getAllByIdIn), or add it to STAFF_ALLOWLIST with a cascade reason or an issue:\n"
                + String.join("\n", violations));

        Set<String> live = new TreeSet<>(risky.stream().filter(FetchPath::staff).map(FetchPath::key).toList());
        List<String> stale = STAFF_ALLOWLIST.keySet().stream().filter(key -> !live.contains(key)).sorted().toList();
        assertTrue(stale.isEmpty(), "STAFF_ALLOWLIST entry no longer matches a risky staff fetch path: " + stale);
    }

    @Test
    void participantFetchPathsThroughAMandatorySoftDeletedRelationAreListedForEvaluation() throws Exception {
        List<FetchPath> risky = riskyFetchPaths();

        List<String> unlisted = risky.stream()
                .filter(path -> !path.staff())
                .filter(path -> !PARTICIPANT_PATHS_AWAITING_EVALUATION.contains(path.key()))
                .map(path -> path.key() + " drops the row when its " + path.droppedBy() + " is soft-deleted")
                .sorted()
                .toList();
        assertTrue(unlisted.isEmpty(), "New participant fetch path through a mandatory soft-deleted relation. "
                + "Decide whether the row must survive the counterpart's deletion; until then list it in "
                + "PARTICIPANT_PATHS_AWAITING_EVALUATION:\n" + String.join("\n", unlisted));

        Set<String> live = new TreeSet<>(risky.stream().filter(path -> !path.staff()).map(FetchPath::key).toList());
        List<String> stale = PARTICIPANT_PATHS_AWAITING_EVALUATION.stream()
                .filter(key -> !live.contains(key))
                .sorted()
                .toList();
        assertTrue(stale.isEmpty(), "PARTICIPANT_PATHS_AWAITING_EVALUATION entry no longer matches a risky "
                + "fetch path: " + stale);
    }

    @Test
    void everyStaffReadMethodStillExists() throws Exception {
        Set<String> methods = new TreeSet<>();
        for (Class<?> dao : daoInterfaces()) {
            for (Method method : dao.getDeclaredMethods()) {
                methods.add(dao.getSimpleName() + "." + method.getName());
            }
        }
        List<String> stale = STAFF_READ_METHODS.keySet().stream().filter(key -> !methods.contains(key)).sorted()
                .toList();
        assertTrue(stale.isEmpty(), "STAFF_READ_METHODS names a DAO method that no longer exists: " + stale);
    }

    @Test
    void theScanIsActuallyLookingAtSomething() throws Exception {
        assertTrue(fetchPaths().size() > 100, "fewer than 100 fetch paths found; the DAO scan is broken");
        assertTrue(riskyFetchPaths().size() > 20, "fewer than 20 risky paths found; the hop analysis is broken");
    }

    private List<FetchPath> riskyFetchPaths() throws Exception {
        Set<String> deletable = deletableEntities();
        List<FetchPath> risky = new ArrayList<>();
        for (FetchPath path : fetchPaths()) {
            Class<?> root = domainTypeOf(Class.forName(DAO_PACKAGE + path.dao()));
            List<String> droppedBy = droppedBy(root, path.path(), deletable);
            if (!droppedBy.isEmpty()) {
                risky.add(new FetchPath(path.dao(), path.method(), path.path(), droppedBy));
            }
        }
        return risky;
    }

    /**
     * Every {@code @EntityGraph} attribute path and every {@code join fetch} of every DAO method, unresolved.
     */
    private List<FetchPath> fetchPaths() throws Exception {
        List<FetchPath> paths = new ArrayList<>();
        for (Class<?> dao : daoInterfaces()) {
            for (Method method : dao.getDeclaredMethods()) {
                EntityGraph graph = method.getAnnotation(EntityGraph.class);
                if (graph != null) {
                    for (String path : graph.attributePaths()) {
                        paths.add(new FetchPath(dao.getSimpleName(), method.getName(), path, List.of()));
                    }
                }
                Query query = method.getAnnotation(Query.class);
                if (query != null) {
                    Matcher matcher = JOIN_FETCH.matcher(query.value());
                    while (matcher.find()) {
                        if (matcher.group(1) == null) {
                            paths.add(new FetchPath(dao.getSimpleName(), method.getName(), matcher.group(2),
                                    List.of()));
                        }
                    }
                }
            }
        }
        return paths;
    }

    /**
     * Walks {@code path} from {@code root}. A hop drops the owning row when the association is mandatory (an INNER
     * join), the target is soft-deleted and something in the codebase deletes the target. Hops after a collection
     * only prune collection elements, never the root, so the walk stops there.
     */
    static List<String> droppedBy(Class<?> root, String path, Set<String> deletable) {
        List<String> droppedBy = new ArrayList<>();
        Class<?> current = root;
        for (String segment : path.split("\\.")) {
            Field field = fieldOf(current, segment);
            if (field == null) {
                throw new IllegalStateException(root.getSimpleName() + " has no association '" + segment
                        + "' on the path " + path);
            }
            if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
                break;
            }
            Class<?> target = field.getType();
            if (mandatory(field) && softDeleted(target) && deletable(target, deletable)) {
                droppedBy.add(target.getSimpleName());
            }
            current = target;
        }
        return droppedBy;
    }

    private static boolean mandatory(Field field) {
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        return (manyToOne != null && !manyToOne.optional())
                || (oneToOne != null && !oneToOne.optional())
                || (joinColumn != null && !joinColumn.nullable());
    }

    private static boolean softDeleted(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.isAnnotationPresent(SQLRestriction.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean deletable(Class<?> type, Set<String> deletable) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (deletable.contains(current.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static Field fieldOf(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep climbing
            }
        }
        return null;
    }

    /**
     * Entities something in {@code src/main} soft-deletes: a {@code *Dao.delete*} call resolved through the field's
     * declared type, an anonymizer {@code update ... is_deleted = true} resolved through the table name, or a
     * {@code orphanRemoval = true} association.
     */
    static Set<String> deletableEntities() throws Exception {
        Set<String> deletable = new TreeSet<>();
        Map<String, Class<?>> daoDomains = new HashMap<>();
        for (Class<?> dao : daoInterfaces()) {
            daoDomains.put(dao.getSimpleName(), domainTypeOf(dao));
        }

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, String> fieldTypes = new LinkedHashMap<>();
                Matcher declaration = DAO_FIELD.matcher(source);
                while (declaration.find()) {
                    fieldTypes.put(declaration.group(2), declaration.group(1));
                }
                Matcher call = DELETE_CALL.matcher(source);
                while (call.find()) {
                    Class<?> domain = daoDomains.get(fieldTypes.get(call.group(1)));
                    if (domain != null) {
                        deletable.add(domain.getSimpleName());
                    }
                }
            }
        }

        Map<String, String> tables = new HashMap<>();
        List<Class<?>> entities = entityClasses();
        for (Class<?> entity : entities) {
            tables.put(tableNameOf(entity), entity.getSimpleName());
        }
        Matcher statement = SOFT_DELETE_STATEMENT.matcher(Files.readString(ANONYMIZER, StandardCharsets.UTF_8));
        while (statement.find()) {
            String entity = tables.get(statement.group(1).toLowerCase(Locale.ROOT));
            if (entity == null) {
                throw new IllegalStateException("anonymizer soft-deletes table '" + statement.group(1)
                        + "' but no entity maps it");
            }
            deletable.add(entity);
        }

        for (Class<?> entity : entities) {
            for (Field field : entity.getDeclaredFields()) {
                if (orphanRemoval(field)) {
                    Class<?> target = Collection.class.isAssignableFrom(field.getType())
                            ? ResolvableType.forField(field).getGeneric(0).resolve()
                            : field.getType();
                    if (target != null) {
                        deletable.add(target.getSimpleName());
                    }
                }
            }
        }
        return deletable;
    }

    private static boolean orphanRemoval(Field field) {
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        return (oneToOne != null && oneToOne.orphanRemoval()) || (oneToMany != null && oneToMany.orphanRemoval());
    }

    private static String tableNameOf(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name().toLowerCase(Locale.ROOT);
        }
        return entity.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static Class<?> entity(String name) throws ClassNotFoundException {
        return Class.forName(ENTITY_PACKAGE + name);
    }

    private static List<Class<?>> entityClasses() throws IOException, ClassNotFoundException {
        List<Class<?>> entities = new ArrayList<>();
        try (Stream<Path> files = Files.list(ENTITIES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                Class<?> type = Class.forName(ENTITY_PACKAGE + file.getFileName().toString().replace(".java", ""));
                if (type.isAnnotationPresent(Entity.class)) {
                    entities.add(type);
                }
            }
        }
        return entities;
    }

    private static List<Class<?>> daoInterfaces() throws IOException, ClassNotFoundException {
        List<Class<?>> daos = new ArrayList<>();
        try (Stream<Path> files = Files.list(DAOS)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Dao.java")).sorted().toList()) {
                Class<?> type = Class.forName(DAO_PACKAGE + file.getFileName().toString().replace(".java", ""));
                if (Repository.class.isAssignableFrom(type)) {
                    daos.add(type);
                }
            }
        }
        return daos;
    }

    private static Class<?> domainTypeOf(Class<?> dao) {
        Class<?> domain = ResolvableType.forClass(Repository.class, dao).getGeneric(0).resolve();
        if (domain == null) {
            throw new IllegalStateException(dao.getSimpleName() + " does not declare its entity type");
        }
        return domain;
    }
}
