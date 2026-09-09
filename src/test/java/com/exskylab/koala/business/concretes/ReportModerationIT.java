package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.core.dtos.report.response.AdminReportDetailDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;
import com.exskylab.koala.entities.ReportTargetType;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.security.StaffPrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportModerationIT extends AbstractStaffIntegrationTest {

    private static final String JOB_TITLE = "Ornek ilan basligi";
    private static final String MESSAGE_BODY = "Bu bir ornek mesaj metnidir.";
    private static final String IMAGE_KEY = "images/ornek-gorsel.jpg";

    @Autowired
    private ReportService reportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID reporterId;
    private UUID reportedUserId;
    private UUID otherReportedUserId;
    private UUID moderatorId;

    private UUID jobId;
    private UUID conversationId;
    private UUID messageId;
    private UUID imageId;

    private UUID openUserReportId;
    private UUID dismissedUserReportId;
    private UUID otherUserReportId;
    private UUID jobReportId;
    private UUID messageReportId;
    private UUID imageReportId;

    @BeforeEach
    void seed() {
        reporterId = UUID.randomUUID();
        reportedUserId = UUID.randomUUID();
        otherReportedUserId = UUID.randomUUID();
        moderatorId = UUID.randomUUID();

        insertUser(reporterId, "Sikayetci", "Kullanici");
        insertUser(reportedUserId, "Sikayet", "Edilen");
        insertUser(otherReportedUserId, "Ikinci", "Edilen");
        insertUser(moderatorId, "Moderator", "Kullanici");

        jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into jobs (id, created_at, creator_id, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age)
                values (?, now(), ?, ?, 'OPEN', 'INDIVIDUAL', false, false, 18, 65)
                """, jobId, reporterId, JOB_TITLE);

        conversationId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into conversations (id, created_at, job_id, employer_id, worker_id)
                values (?, now(), ?, ?, ?)
                """, conversationId, jobId, reporterId, reportedUserId);

        messageId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into messages (id, created_at, conversation_id, sender_id, body, sent_at)
                values (?, now(), ?, ?, ?, now())
                """, messageId, conversationId, reportedUserId, MESSAGE_BODY);

        imageId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into media (id, created_at, media_type, file_name, file_type, file_url, file_size)
                values (?, now(), 'IMAGE', 'ornek-gorsel.jpg', 'image/jpeg', ?, 1024)
                """, imageId, IMAGE_KEY);

        openUserReportId = insertReport(reportedUserId, null, null, null, "SPAM", "OPEN", false);
        dismissedUserReportId =
                insertReport(reportedUserId, null, null, null, "HARASSMENT", "DISMISSED", true);
        otherUserReportId = insertReport(otherReportedUserId, null, null, null, "FRAUD", "OPEN", false);
        jobReportId = insertReport(null, jobId, null, null, "INAPPROPRIATE_CONTENT", "OPEN", false);
        messageReportId = insertReport(null, null, messageId, null, "HARASSMENT", "UNDER_REVIEW", false);
        imageReportId = insertReport(null, null, null, imageId, "INAPPROPRIATE_CONTENT", "OPEN", false);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reports where reporter_id = ?", reporterId);
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'decision-%@iskoala.com')");
        jdbcTemplate.update("delete from staff_members where email like 'decision-%@iskoala.com'");
        jdbcTemplate.update("delete from media where id = ?", imageId);
        jdbcTemplate.update("delete from messages where conversation_id = ?", conversationId);
        jdbcTemplate.update("delete from conversations where id = ?", conversationId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from users where id in (?, ?, ?, ?)",
                reporterId, reportedUserId, otherReportedUserId, moderatorId);
    }

    private void insertUser(UUID id, String firstName, String lastName) {
        jdbcTemplate.update("""
                insert into users (id, created_at, email, first_name, last_name,
                                   email_verified, phone_verified, identity_verified, is_employer)
                values (?, now(), ?, ?, ?, true, true, true, false)
                """, id, "moderation-" + id + "@koala.test", firstName, lastName);
    }

    private UUID insertReport(UUID reportedUser, UUID job, UUID message, UUID image,
                              String reason, String status, boolean reviewed) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into reports (id, created_at, reporter_id, reported_user_id, job_id, message_id,
                                     image_id, reason, status, description, reviewed_by_id, review_note,
                                     reviewed_at)
                values (?, now(), ?, ?, ?, ?, ?, ?, ?, 'Ornek sikayet aciklamasi', ?, ?, ?)
                """, id, reporterId, reportedUser, job, message, image, reason, status,
                reviewed ? moderatorId : null,
                reviewed ? "Ornek karar gerekcesi" : null,
                reviewed ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        return id;
    }

    private List<UUID> idsIn(Page<AdminReportDto> page) {
        return page.getContent().stream().map(AdminReportDto::id).toList();
    }

    private AdminReportDto rowFor(UUID reportId) {
        return reportService.searchForModeration(null, null, null, null, PageRequest.of(0, 100))
                .getContent().stream()
                .filter(row -> row.id().equals(reportId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sikayet kuyrukta yok: " + reportId));
    }

    private Page<AdminReportDto> queue(ReportStatus status, ReportReason reason,
                                       ReportTargetType targetType, UUID reportedUser) {
        return reportService.searchForModeration(status, reason, targetType, reportedUser,
                PageRequest.of(0, 100));
    }

    @Test
    void theQueueWithoutAStatusFilterShowsEveryStatus() {
        List<UUID> all = idsIn(queue(null, null, null, null));

        assertTrue(all.contains(openUserReportId), "Durum verilmediginde OPEN sikayet kuyrukta olmali.");
        assertTrue(all.contains(dismissedUserReportId),
                "Durum verilmediginde DISMISSED sikayet de kuyrukta olmali; 'tum durumlar' gorunumu budur.");
        assertTrue(all.contains(messageReportId), "UNDER_REVIEW sikayet de kuyrukta olmali.");
    }

    @Test
    void aStatusFilterStillNarrowsTheQueue() {
        List<UUID> open = idsIn(queue(ReportStatus.OPEN, null, null, null));

        assertTrue(open.contains(openUserReportId));
        assertFalse(open.contains(dismissedUserReportId), "Durum verildiginde kuyruk daralmali.");
    }

    @Test
    void aReportWithoutAReportedUserStaysInTheUnfilteredQueue() {
        List<UUID> all = idsIn(queue(null, null, null, null));

        assertTrue(all.contains(jobReportId),
                "Hedefi ilan olan sikayetin sikayet edileni yok; susuz kuyruktan dusmemeli.");
        assertTrue(all.contains(messageReportId), "Hedefi mesaj olan sikayet de dusmemeli.");
        assertTrue(all.contains(imageReportId), "Hedefi gorsel olan sikayet de dusmemeli.");
    }

    @Test
    void theReasonFilterNarrowsTheQueue() {
        List<UUID> fraud = idsIn(queue(null, ReportReason.FRAUD, null, null));

        assertTrue(fraud.contains(otherUserReportId));
        assertFalse(fraud.contains(openUserReportId), "SPAM sikayeti FRAUD suzgecinde gorunmemeli.");
    }

    @Test
    void theTargetTypeFilterSeparatesTheFourTargets() {
        assertTrue(idsIn(queue(null, null, ReportTargetType.JOB, null)).contains(jobReportId));
        assertTrue(idsIn(queue(null, null, ReportTargetType.MESSAGE, null)).contains(messageReportId));
        assertTrue(idsIn(queue(null, null, ReportTargetType.IMAGE, null)).contains(imageReportId));

        List<UUID> aboutUsers = idsIn(queue(null, null, ReportTargetType.USER, null));
        assertTrue(aboutUsers.contains(openUserReportId));
        assertFalse(aboutUsers.contains(jobReportId),
                "Hedef tipi USER secildiginde ilan sikayeti kuyrukta kalmamali.");
    }

    @Test
    void theReportedUserFilterGroupsEveryReportAboutOnePerson() {
        List<UUID> aboutOnePerson = idsIn(queue(null, null, null, reportedUserId));

        assertTrue(aboutOnePerson.contains(openUserReportId));
        assertTrue(aboutOnePerson.contains(dismissedUserReportId),
                "Ayni kisi hakkindaki sikayetler durumdan bagimsiz gruplanmali.");
        assertFalse(aboutOnePerson.contains(otherUserReportId));
        assertFalse(aboutOnePerson.contains(jobReportId));
    }

    @Test
    void theQueueRowNamesTheReporter() {
        AdminReportDto row = rowFor(openUserReportId);

        assertEquals(reporterId, row.reporter().getId());
        assertEquals("Sikayetci", row.reporter().getFirstName(),
                "Kuyruktaki sikayet eden sutunu bos kalmamali.");
    }

    @Test
    void theQueueRowNamesTheModeratorWhoDecidedAndLeavesItEmptyUntilThen() {
        assertEquals(moderatorId, rowFor(dismissedUserReportId).reviewedBy().getId(),
                "Sonuclanmis sikayette karari kimin verdigi okunabilmeli.");
        assertNull(rowFor(openUserReportId).reviewedBy(),
                "Henuz karara baglanmamis sikayette reviewedBy bos kalmali.");
    }

    @Test
    void theQueueRowCarriesTheReportedUserAsAWholePerson() {
        AdminReportDto row = rowFor(openUserReportId);

        assertEquals(reportedUserId, row.reportedUser().getId());
        assertEquals("Edilen", row.reportedUser().getLastName());
        assertNull(rowFor(jobReportId).reportedUser(),
                "Hedefi ilan olan sikayette sikayet edilen kullanici yoktur.");
    }

    @Test
    void theQueueRowCountsEveryReportEverFiledAboutTheReportedUser() {
        assertEquals(2L, rowFor(openUserReportId).reportedUserReportCount(),
                "Sikayet edilen hakkinda iki kayit var.");
        assertEquals(1L, rowFor(otherUserReportId).reportedUserReportCount());
        assertEquals(0L, rowFor(jobReportId).reportedUserReportCount(),
                "Sikayet edileni olmayan kayitta gecmis sayisi sifir olmali.");
    }

    @Test
    void theHistoryCountComesFromTheWholeQueueNotFromTheVisiblePage() {
        Page<AdminReportDto> onePerPage = reportService.searchForModeration(
                null, null, null, reportedUserId, PageRequest.of(0, 1));

        assertEquals(1, onePerPage.getContent().size(), "Sayfa boyutu bir olmali.");
        assertEquals(2L, onePerPage.getContent().get(0).reportedUserReportCount(),
                "Sayfada tek satir gorunuyor ama toplam iki; sayi sayfa kapsamli olamaz.");
    }

    @Test
    void aDecidedReportStillCountsTowardsTheHistory() {
        assertEquals(2L, rowFor(dismissedUserReportId).reportedUserReportCount(),
                "Sonuclanmis sikayet de kisinin gecmisinin parcasidir.");
    }

    @Test
    void theDetailOfAJobReportMakesTheListingReadable() {
        AdminReportDetailDto detail = reportService.getForModeration(jobReportId);

        assertEquals(JOB_TITLE, detail.content().jobTitle(),
                "Operator degerlendirdigi ilanin basligini gormeli, yalnizca UUID'sini degil.");
        assertNull(detail.content().messageBody());
        assertNull(detail.content().imageUrl());
    }

    @Test
    void theDetailOfAMessageReportMakesTheMessageReadable() {
        AdminReportDetailDto detail = reportService.getForModeration(messageReportId);

        assertEquals(MESSAGE_BODY, detail.content().messageBody(),
                "Sikayet edilen mesajin metni detayda okunabilmeli.");
        assertNull(detail.content().jobTitle());
    }

    @Test
    void theDetailOfAnImageReportCarriesAReachableUrl() {
        AdminReportDetailDto detail = reportService.getForModeration(imageReportId);

        assertNotNull(detail.content().imageUrl());
        assertTrue(detail.content().imageUrl().endsWith(IMAGE_KEY),
                "Gorsel URL'i depodaki anahtari isaret etmeli: " + detail.content().imageUrl());
    }

    @Test
    void theDetailRepeatsTheIdentityAndTheHistoryTheQueueShows() {
        AdminReportDetailDto detail = reportService.getForModeration(dismissedUserReportId);

        assertEquals(reporterId, detail.report().reporter().getId());
        assertEquals(moderatorId, detail.report().reviewedBy().getId());
        assertEquals(2L, detail.report().reportedUserReportCount());
    }

    @Test
    void anUnknownReportHasNoDetail() {
        UUID unknown = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> reportService.getForModeration(unknown));
    }

    @Test
    void aStaffModeratorCanActuallyDecideAndGetsTheWholeQueueRowBack() {
        UUID staffId = createStaffMember("decision-" + UUID.randomUUID() + "@iskoala.com",
                "Moderator", "MODERATOR");
        StaffPrincipal moderator = new StaffPrincipal(staffId, "moderator@iskoala.com",
                "Moderator", Set.of("MODERATOR"), Set.of(), false);

        AdminReportDto decided = reportService.review(moderator, openUserReportId,
                ReportStatus.ACTIONED, "Hesap askiya alindi.");

        assertEquals(ReportStatus.ACTIONED, decided.status());
        assertEquals(reporterId, decided.reporter().getId(),
                "Karar yaniti kuyruk satirinin tamamidir; panel ikinci bir istek atmamali.");
        assertEquals(2L, decided.reportedUserReportCount());
        assertNull(decided.reviewedBy(),
                "Personel karari eski marketplace reviewedBy alanini doldurmamali.");
        assertEquals(staffId, jdbcTemplate.queryForObject(
                "select reviewed_by_staff_id from reports where id = ?", UUID.class, openUserReportId));
        assertNull(jdbcTemplate.queryForObject(
                "select reviewed_by_id from reports where id = ?", UUID.class, openUserReportId));
    }
}
