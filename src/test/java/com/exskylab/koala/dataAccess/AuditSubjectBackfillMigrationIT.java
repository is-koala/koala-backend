package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditSubjectBackfillMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "audit_subject_backfill_probe";
    private static final String CHANGE_LOG = "classpath:/db/changelog/2026/10/03-01-audit-subject.xml";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private JdbcTemplate probe;

    private final UUID worker = UUID.randomUUID();
    private final UUID creator = UUID.randomUUID();
    private final UUID reportedUser = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();
    private final UUID individualJob = UUID.randomUUID();
    private final UUID companyJob = UUID.randomUUID();
    private final UUID individualAssignment = UUID.randomUUID();
    private final UUID companyAssignment = UUID.randomUUID();

    @BeforeEach
    void createPreMigrationSchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("""
                create table admin_audit_entries (
                    id UUID primary key,
                    occurred_at TIMESTAMPTZ not null,
                    resource_type VARCHAR(64) not null,
                    resource_id VARCHAR(128) not null,
                    mutating BOOLEAN not null
                )
                """);
        probe.execute("create table users (id UUID primary key)");
        probe.execute("create table companies (id UUID primary key)");
        probe.execute("create table jobs (id UUID primary key, creator_id UUID, company_id UUID)");
        probe.execute("create table job_assignments (id UUID primary key, job_id UUID not null, worker_id UUID not null)");
        probe.execute("create table user_verifications (id UUID primary key, user_id UUID)");
        probe.execute("create table account_deletion_requests (id UUID primary key, user_id UUID, subject_id UUID)");
        probe.execute("create table company_images (id UUID primary key, company_id UUID not null, image_id UUID not null)");
        probe.execute("create table job_images (id UUID primary key, job_id UUID not null, image_id UUID not null)");
        probe.execute("create table bank_accounts (id UUID primary key, user_id UUID not null, "
                + "verification_reference VARCHAR(32))");
        probe.execute("create table payouts (id UUID primary key, worker_id UUID not null)");
        probe.execute("create table disputes (id UUID primary key, job_assignment_id UUID not null)");
        probe.execute("create table reports (id UUID primary key, reported_user_id UUID, job_id UUID)");

        probe.update("insert into users (id) values (?), (?), (?)", worker, creator, reportedUser);
        probe.update("insert into companies (id) values (?)", company);
        probe.update("insert into jobs (id, creator_id, company_id) values (?, ?, null), (?, ?, ?)",
                individualJob, creator, companyJob, creator, company);
        probe.update("insert into job_assignments (id, job_id, worker_id) values (?, ?, ?), (?, ?, ?)",
                individualAssignment, individualJob, worker, companyAssignment, companyJob, worker);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource).execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void decisionsRecordedBeforeTheColumnsExistedGetTheSubjectTheResolverWouldHaveWritten() {
        UUID review = UUID.randomUUID();
        UUID deletion = UUID.randomUUID();
        UUID payout = UUID.randomUUID();
        UUID dispute = UUID.randomUUID();
        UUID userReport = UUID.randomUUID();
        UUID jobReport = UUID.randomUUID();
        probe.update("insert into user_verifications (id, user_id) values (?, ?)", review, worker);
        probe.update("insert into account_deletion_requests (id, user_id, subject_id) values (?, null, ?)",
                deletion, worker);
        probe.update("insert into payouts (id, worker_id) values (?, ?)", payout, worker);
        probe.update("insert into disputes (id, job_assignment_id) values (?, ?)", dispute, companyAssignment);
        probe.update("insert into reports (id, reported_user_id, job_id) values (?, ?, null), (?, null, ?)",
                userReport, reportedUser, jobReport, companyJob);

        UUID roleGrant = audit("users", worker, true);
        UUID userRead = audit("users", worker, false);
        UUID companyDecision = audit("companies", company, true);
        UUID reviewDecision = audit("identity-verifications", review, true);
        UUID deletionDecision = audit("account-deletions", deletion, true);
        UUID payoutDecision = audit("payouts", payout, true);
        UUID individualRelease = audit("job-assignments", individualAssignment, true);
        UUID companyRefund = audit("job-assignments", companyAssignment, true);
        UUID disputeDecision = audit("disputes", dispute, true);
        UUID userReportDecision = audit("reports", userReport, true);
        UUID jobReportDecision = audit("reports", jobReport, true);
        UUID unknownAssignment = audit("job-assignments", UUID.randomUUID(), true);

        applyChangeLog();

        assertSubject(roleGrant, "USER", worker, null, null);
        assertSubject(userRead, null, null, null, null);
        assertSubject(companyDecision, "COMPANY", company, null, null);
        assertSubject(reviewDecision, "USER", worker, null, null);
        assertSubject(deletionDecision, "USER", worker, null, null);
        assertSubject(payoutDecision, "USER", worker, null, null);
        assertSubject(individualRelease, "USER", worker, "USER", creator);
        assertSubject(companyRefund, "USER", worker, "COMPANY", company);
        assertSubject(disputeDecision, "USER", worker, "COMPANY", company);
        assertSubject(userReportDecision, "USER", reportedUser, null, null);
        assertSubject(jobReportDecision, "COMPANY", company, null, null);
        assertSubject(unknownAssignment, null, null, null, null);
    }

    @Test
    void imagesAndBankVerificationsResolveThroughTheirOwnTables() {
        UUID companyImage = UUID.randomUUID();
        UUID jobImage = UUID.randomUUID();
        String reference = "KOALA-AB12CD34";
        probe.update("insert into company_images (id, company_id, image_id) values (?, ?, ?)",
                UUID.randomUUID(), company, companyImage);
        probe.update("insert into job_images (id, job_id, image_id) values (?, ?, ?)",
                UUID.randomUUID(), individualJob, jobImage);
        probe.update("insert into bank_accounts (id, user_id, verification_reference) values (?, ?, ?)",
                UUID.randomUUID(), worker, reference);

        UUID companyImageRemoval = audit("images", companyImage, true);
        UUID jobImageRemoval = audit("images", jobImage, true);
        UUID bankConfirmation = auditRaw("bank-account-verifications", reference, true);
        UUID settingChange = auditRaw("settings", "cancellation.late-fee-rate", true);

        applyChangeLog();

        assertSubject(companyImageRemoval, "COMPANY", company, null, null);
        assertSubject(jobImageRemoval, "USER", creator, null, null);
        assertSubject(bankConfirmation, "USER", worker, null, null);
        assertSubject(settingChange, null, null, null, null);
    }

    @Test
    void theColumnsRefuseAHalfWrittenSubject() {
        applyChangeLog();

        UUID row = audit("users", worker, true);
        assertThrows(DataIntegrityViolationException.class, () -> probe.update(
                "update admin_audit_entries set subject_type = 'USER' where id = ?", row));
        assertThrows(DataIntegrityViolationException.class, () -> probe.update(
                "update admin_audit_entries set subject_type = 'STAFF', subject_id = ? where id = ?", worker, row));
        assertThrows(DataIntegrityViolationException.class, () -> probe.update(
                "update admin_audit_entries set related_subject_type = 'USER', related_subject_id = ? where id = ?",
                creator, row));
    }

    private UUID audit(String resourceType, UUID resourceId, boolean mutating) {
        return auditRaw(resourceType, resourceId.toString(), mutating);
    }

    private UUID auditRaw(String resourceType, String resourceId, boolean mutating) {
        UUID id = UUID.randomUUID();
        probe.update("""
                insert into admin_audit_entries (id, occurred_at, resource_type, resource_id, mutating)
                values (?, now(), ?, ?, ?)
                """, id, resourceType, resourceId, mutating);
        return id;
    }

    private void assertSubject(UUID row, String type, UUID id, String relatedType, UUID relatedId) {
        Map<String, Object> values = probe.queryForMap("""
                select subject_type, subject_id, related_subject_type, related_subject_id
                from admin_audit_entries where id = ?
                """, row);
        assertEquals(type, values.get("subject_type"), "subject_type: " + values);
        assertEquals(id, values.get("subject_id"), "subject_id: " + values);
        assertEquals(relatedType, values.get("related_subject_type"), "related_subject_type: " + values);
        assertEquals(relatedId, values.get("related_subject_id"), "related_subject_id: " + values);
        if (type == null) {
            assertNull(values.get("subject_id"));
        }
    }

    private void applyChangeLog() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(probeDataSource());
        liquibase.setChangeLog(CHANGE_LOG);
        liquibase.setResourceLoader(resourceLoader);
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DataSource probeDataSource() {
        DriverManagerDataSource probeSource = new DriverManagerDataSource();
        probeSource.setUrl(probeUrl());
        probeSource.setUsername(username);
        probeSource.setPassword(password);
        return probeSource;
    }

    private String probeUrl() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            int database = url.lastIndexOf('/');
            int query = url.indexOf('?', database);
            String suffix = query < 0 ? "" : url.substring(query);
            return url.substring(0, database + 1) + PROBE_DATABASE + suffix;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
