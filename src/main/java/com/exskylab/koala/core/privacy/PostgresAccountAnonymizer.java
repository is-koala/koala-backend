package com.exskylab.koala.core.privacy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Component
public class PostgresAccountAnonymizer implements AccountAnonymizer {

    private static final String REDACTED = "[anonymized]";

    private final JdbcTemplate jdbcTemplate;
    private final PersonalMediaDeletionOutbox mediaDeletionOutbox;

    public PostgresAccountAnonymizer(JdbcTemplate jdbcTemplate,
                                     PersonalMediaDeletionOutbox mediaDeletionOutbox) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaDeletionOutbox = mediaDeletionOutbox;
    }

    @Override
    @Transactional
    public void anonymize(UUID userId, UUID requestId) {
        audit(requestId, "STARTED");
        removeMedia(userId);
        audit(requestId, "MEDIA_REMOVED");
        scrubUserContent(userId);
        audit(requestId, "CONTENT_SCRUBBED");
        revokeCredentials(userId);
        audit(requestId, "CREDENTIALS_REVOKED");
        scrubProfile(userId);
        audit(requestId, "PROFILE_SCRUBBED");
        audit(requestId, "COMPLETED");
    }

    private void removeMedia(UUID userId) {
        List<String> keys = jdbcTemplate.queryForList("""
                select distinct m.file_url
                from media m
                where m.file_url is not null
                  and (m.id in (select profile_picture_id from users where id = ?)
                    or m.id in (select cv_id from users where id = ?)
                    or m.id in (select verification_document_id from user_verifications where user_id = ?))
                """, String.class, userId, userId, userId);
        jdbcTemplate.update("""
                update media
                   set file_name = ?, file_type = null, file_url = null, file_size = 0,
                       is_deleted = true, deleted_at = now()
                 where id in (select profile_picture_id from users where id = ?)
                    or id in (select cv_id from users where id = ?)
                    or id in (select verification_document_id from user_verifications where user_id = ?)
                """, REDACTED, userId, userId, userId);
        mediaDeletionOutbox.enqueue(keys);
        processMediaAfterCommit(keys);
    }

    private void scrubUserContent(UUID userId) {
        jdbcTemplate.update("update messages set body = ? where sender_id = ?", REDACTED, userId);
        jdbcTemplate.update("update conversations set last_message_body = null where employer_id = ? or worker_id = ?",
                userId, userId);
        jdbcTemplate.update("update reviews set comment = null, anonymous = true where author_id = ? or subject_id = ?",
                userId, userId);
        jdbcTemplate.update("update user_blocks set reason = null where blocker_id = ? or blocked_id = ?",
                userId, userId);
        jdbcTemplate.update("""
                update cancellations
                   set reason = null
                 where cancelled_by_id = ?
                    or job_assignment_id in (
                       select a.id from job_assignments a
                       join jobs j on j.id = a.job_id
                       where a.worker_id = ? or j.creator_id = ?)
                """, userId, userId, userId);
        jdbcTemplate.update("update reports set description = null where reporter_id = ? or reported_user_id = ?",
                userId, userId);
        jdbcTemplate.update("update disputes set reason = ? where opened_by_id = ?", REDACTED, userId);
        jdbcTemplate.update("""
                update disputes
                   set reason = ?, resolution_note = null
                 where job_assignment_id in (
                       select a.id from job_assignments a
                       join jobs j on j.id = a.job_id
                       where a.worker_id = ? or j.creator_id = ?)
                """, REDACTED, userId, userId);
        jdbcTemplate.update("""
                update job_applications
                   set notes = null, rejection_reason = null
                 where user_id = ? or job_id in (select id from jobs where creator_id = ?)
                """, userId, userId);
        jdbcTemplate.update("""
                update work_sessions
                   set check_in_latitude = null, check_in_longitude = null,
                       check_out_latitude = null, check_out_longitude = null
                 where job_assignment_id in (
                       select a.id from job_assignments a
                       join jobs j on j.id = a.job_id
                       where a.worker_id = ? or j.creator_id = ?)
                """, userId, userId);
        jdbcTemplate.update("update educations set is_deleted = true, deleted_at = now() where user_id = ?", userId);
        jdbcTemplate.update("""
                update notifications
                   set destination_phone_number = null, destination_email = null,
                       destination_push_token = null, subject = null, body = null,
                       template_parameters = null, external_id = null, failure_reason = null
                 where recipient_id = ? or sender_id = ?
                """, userId, userId);
        jdbcTemplate.update("""
                update experiences
                   set title = ?, company_name = null, city_name = null, description = null,
                       is_deleted = true, deleted_at = now()
                 where user_id = ?
                """, REDACTED, userId);
        jdbcTemplate.update("update availability_exceptions set note = null, is_deleted = true, deleted_at = now() where user_id = ?",
                userId);
        jdbcTemplate.update("update availability_slots set is_deleted = true, deleted_at = now() where user_id = ?", userId);
        jdbcTemplate.update("update job_alerts set alert_name = ?, city_name = null, district_name = null, is_active = false, is_deleted = true, deleted_at = now() where user_id = ?",
                REDACTED, userId);
        jdbcTemplate.update("update job_templates set template_name = ?, title = null, description = null, sector = null, position = null, duties = null, dress_code = null, is_deleted = true, deleted_at = now() where owner_id = ?",
                REDACTED, userId);
        jdbcTemplate.update("update talent_pool_entries set note = null, is_deleted = true, deleted_at = now() where employer_id = ? or worker_id = ?",
                userId, userId);
        jdbcTemplate.update("""
                update addresses
                   set neighborhood = null, thoroughfare = null, site_name = null, block = null,
                       building_name = null, building_number = null, floor = null,
                       unit_number = null, postal_code = null, additional_directions = null,
                       latitude = null, longitude = null, position_source = null,
                       confirmed_at = null, needs_review = false
                 where id in (
                       select address_id from jobs
                       where creator_id = ? and address_id is not null
                         and (employer_type = 'INDIVIDUAL' or company_id is null))
                """, userId);
        jdbcTemplate.update("""
                update jobs set address_public = false
                 where creator_id = ? and (employer_type = 'INDIVIDUAL' or company_id is null)
                """, userId);
    }

    private void revokeCredentials(UUID userId) {
        jdbcTemplate.update("""
                update sessions
                   set refresh_token_hash = null, ip_address = null, location = null, is_active = false
                 where device_id in (select id from devices where user_id = ?)
                """, userId);
        jdbcTemplate.update("update devices set name = null, client_device_id = null, push_token = null, is_deleted = true, deleted_at = now() where user_id = ?",
                userId);
        jdbcTemplate.update("update login_attempts set email_attempted = null, failure_reason = null, ip_address = null, device_name = null where user_id = ?",
                userId);
        jdbcTemplate.update("update identity_challenges set challenge = id::text, is_deleted = true, deleted_at = now() where user_id = ?",
                userId);
        jdbcTemplate.update("""
                update password_reset_tokens
                   set revoked_at = coalesce(revoked_at, now()),
                       is_deleted = true, deleted_at = coalesce(deleted_at, now())
                 where user_id = ?
                """, userId);
        jdbcTemplate.update("""
                update user_verifications
                   set old_phone_number = null, new_phone_number = null, token = null,
                       tc_identity_number = null, verification_document_id = null,
                       old_email = null, new_email = null, is_deleted = true, deleted_at = now()
                 where user_id = ?
                """, userId);
        jdbcTemplate.update("delete from authorities where user_id = ?", userId);
        jdbcTemplate.update("delete from users_saved_jobs where user_id = ?", userId);
        jdbcTemplate.update("update user_communication_preferences set email_enabled = false, sms_enabled = false, push_enabled = false, is_deleted = true, deleted_at = now() where user_id = ?",
                userId);
        jdbcTemplate.update("update notification_preferences set enabled = false, is_deleted = true, deleted_at = coalesce(deleted_at, now()) where user_id = ?",
                userId);
        jdbcTemplate.update("update bank_accounts set iban = ?, account_holder_name = null, is_primary = false, is_active = false, is_deleted = true, deleted_at = now() where user_id = ?",
                REDACTED, userId);
        jdbcTemplate.update("update consent_records set ip_address = null, user_agent = null where user_id = ?", userId);
        jdbcTemplate.update("update commercial_message_consents set ip_address = null, user_agent = null where user_id = ?",
                userId);
        jdbcTemplate.update("""
                update iys_consent_tasks
                   set recipient = ?, last_failure_code = null,
                       status = case when status in ('PENDING', 'PROCESSING') then 'SUPERSEDED' else status end,
                       processing_started_at = null,
                       completed_at = case when status in ('PENDING', 'PROCESSING')
                                           then coalesce(completed_at, now()) else completed_at end
                 where user_id = ?
                """, REDACTED, userId);
        jdbcTemplate.update("""
                update iys_permission_states
                   set recipient = ?, provider_source = null, last_failure_code = null,
                       is_deleted = true, deleted_at = coalesce(deleted_at, now())
                 where user_id = ?
                """, REDACTED, userId);
        jdbcTemplate.update("update unsubscribe_tokens set is_deleted = true, deleted_at = coalesce(deleted_at, now()), expires_at = least(expires_at, now()) where user_id = ?",
                userId);
    }

    private void scrubProfile(UUID userId) {
        jdbcTemplate.update("""
                update addresses
                   set neighborhood = null, thoroughfare = null, site_name = null, block = null,
                       building_name = null, building_number = null, floor = null, unit_number = null,
                       postal_code = null, additional_directions = null, latitude = null, longitude = null,
                       position_source = null, confirmed_at = null, needs_review = false,
                       is_deleted = true, deleted_at = now()
                 where id in (select address_id from users where id = ?)
                """, userId);
        jdbcTemplate.update("""
                update users
                   set first_name = ?, last_name = ?, phone_number = null,
                       username = concat('deleted-', id),
                       email = concat('deleted+', replace(id::text, '-', ''), '@anonymous.invalid'),
                       profile_picture_id = null, password = null, birth_date = null, gender = null,
                       tc_identity_number = null, tc_document_number = null,
                       identity_evidence_digest = null, identity_document_signer = null,
                       job_title = null, about = null, qualifications = null, interests = null,
                       email_verified = false, phone_verified = false, identity_verified = false,
                       cv_id = null, address_id = null, iyzico_submerchant_key = null,
                       is_deleted = true, deleted_at = now()
                 where id = ?
                """, "Anonim", "Kullanıcı", userId);
    }

    private void audit(UUID requestId, String step) {
        jdbcTemplate.update("""
                insert into account_anonymization_audit (id, request_id, step, occurred_at)
                values (?, ?, ?, now())
                on conflict (request_id, step) do nothing
                """, UUID.randomUUID(), requestId, step);
    }

    private void processMediaAfterCommit(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mediaDeletionOutbox.process(keys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mediaDeletionOutbox.process(keys);
            }
        });
    }
}
