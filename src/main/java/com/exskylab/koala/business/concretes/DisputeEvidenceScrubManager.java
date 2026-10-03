package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DisputeEvidenceScrubService;
import com.exskylab.koala.core.dtos.dispute.response.EvidenceScrubFailureDto;
import com.exskylab.koala.core.dtos.dispute.response.EvidenceScrubReportDto;
import com.exskylab.koala.core.helpers.Checksums;
import com.exskylab.koala.core.helpers.ImageMetadataStripper;
import com.exskylab.koala.core.helpers.StorageRollbackCleanup;
import com.exskylab.koala.core.privacy.PersonalMediaDeletionOutbox;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.DisputeEvidenceDao;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.entities.EvidenceScrubFailure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class DisputeEvidenceScrubManager implements DisputeEvidenceScrubService {

    static final Set<String> IMAGE_CONTENT_TYPES = Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);
    static final int MAX_BATCH = 100;

    private final DisputeEvidenceDao disputeEvidenceDao;
    private final StorageService storageService;
    private final PersonalMediaDeletionOutbox mediaDeletionOutbox;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DisputeEvidenceScrubManager(DisputeEvidenceDao disputeEvidenceDao, StorageService storageService,
                                       PersonalMediaDeletionOutbox mediaDeletionOutbox,
                                       PlatformTransactionManager transactionManager, Clock clock) {
        this.disputeEvidenceDao = disputeEvidenceDao;
        this.storageService = storageService;
        this.mediaDeletionOutbox = mediaDeletionOutbox;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public EvidenceScrubReportDto scrubStoredEvidence(StaffPrincipal actor, int limit, boolean retryFailed) {
        int batch = Math.clamp(limit, 1, MAX_BATCH);
        UUID staffId = actor == null ? null : actor.staffId();
        List<UUID> candidates = disputeEvidenceDao.findScrubCandidateIds(IMAGE_CONTENT_TYPES, retryFailed,
                PageRequest.of(0, batch));

        List<UUID> scrubbed = new ArrayList<>();
        List<EvidenceScrubFailureDto> failed = new ArrayList<>();
        for (UUID evidenceId : candidates) {
            Outcome outcome = scrubOne(evidenceId, staffId);
            if (outcome.failure() != null) {
                failed.add(new EvidenceScrubFailureDto(evidenceId, outcome.failure()));
            } else if (outcome.scrubbed()) {
                scrubbed.add(evidenceId);
            }
        }

        long remaining = disputeEvidenceDao.countScrubCandidates(IMAGE_CONTENT_TYPES, false);
        log.info("Dispute evidence metadata scrub by staff {}: {} scrubbed, {} failed, {} remaining",
                staffId, scrubbed.size(), failed.size(), remaining);
        StaffAuditContext.capture("candidates=" + candidates, "scrubbed=" + scrubbed + "; failed="
                + failed.stream().map(f -> f.evidenceId() + ":" + f.reason()).toList() + "; remaining=" + remaining);
        return new EvidenceScrubReportDto(scrubbed.size(), failed, remaining);
    }

    private record Outcome(boolean scrubbed, EvidenceScrubFailure failure) {

        private static final Outcome SCRUBBED = new Outcome(true, null);
        private static final Outcome SKIPPED = new Outcome(false, null);

        private static Outcome failedWith(EvidenceScrubFailure failure) {
            return new Outcome(false, failure);
        }
    }

    private Outcome scrubOne(UUID evidenceId, UUID staffId) {
        try {
            return transactions.execute(status -> scrubInTransaction(evidenceId, staffId));
        } catch (RuntimeException e) {
            log.error("Dispute evidence {} could not be rewritten; the original stays in place", evidenceId, e);
            transactions.executeWithoutResult(status -> disputeEvidenceDao.findById(evidenceId)
                    .ifPresent(evidence -> evidence.setMetadataScrubError(EvidenceScrubFailure.REWRITE_FAILED)));
            return Outcome.failedWith(EvidenceScrubFailure.REWRITE_FAILED);
        }
    }

    private Outcome scrubInTransaction(UUID evidenceId, UUID staffId) {
        Optional<DisputeEvidence> locked = disputeEvidenceDao.findByIdForUpdate(evidenceId);
        if (locked.isEmpty() || locked.get().getMetadataScrubbedAt() != null
                || locked.get().getStorageKey() == null) {
            return Outcome.SKIPPED;
        }
        DisputeEvidence evidence = locked.get();

        byte[] original;
        try {
            original = storageService.readFile(evidence.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("Dispute evidence {} object could not be read for scrubbing", evidenceId, e);
            return failed(evidence, EvidenceScrubFailure.OBJECT_UNREADABLE);
        }
        if (!Checksums.sha256Hex(original).equals(evidence.getChecksumSha256())) {
            log.warn("Dispute evidence {} no longer matches its recorded checksum; left untouched", evidenceId);
            return failed(evidence, EvidenceScrubFailure.CHECKSUM_MISMATCH);
        }

        byte[] clean;
        try {
            clean = ImageMetadataStripper.strip(original,
                    ImageMetadataStripper.formatOfContentType(evidence.getContentType()));
        } catch (IOException e) {
            log.warn("Dispute evidence {} could not be decoded for scrubbing", evidenceId, e);
            return failed(evidence, EvidenceScrubFailure.IMAGE_UNREADABLE);
        }

        String cleanChecksum = Checksums.sha256Hex(clean);
        String newKey = storageService.uploadFile(clean, evidence.getOriginalFileName(), evidence.getContentType(),
                FolderType.DISPUTE_EVIDENCE);
        StorageRollbackCleanup.deleteOnRollback(storageService, newKey);
        if (!cleanChecksum.equals(Checksums.sha256Hex(storageService.readFile(newKey)))) {
            throw new IllegalStateException("The rewritten copy of dispute evidence " + evidenceId
                    + " does not read back as written");
        }

        String previousKey = evidence.getStorageKey();
        evidence.setPreScrubFileSize(evidence.getFileSize());
        evidence.setPreScrubChecksumSha256(evidence.getChecksumSha256());
        evidence.setStorageKey(newKey);
        evidence.setFileSize(clean.length);
        evidence.setChecksumSha256(cleanChecksum);
        evidence.setMetadataScrubbedAt(clock.instant());
        evidence.setMetadataScrubbedByStaffId(staffId);
        evidence.setMetadataScrubError(null);
        disputeEvidenceDao.saveAndFlush(evidence);
        mediaDeletionOutbox.enqueue(List.of(previousKey));
        log.info("Dispute evidence {} metadata scrubbed by staff {}", evidenceId, staffId);
        return Outcome.SCRUBBED;
    }

    private Outcome failed(DisputeEvidence evidence, EvidenceScrubFailure reason) {
        evidence.setMetadataScrubError(reason);
        return Outcome.failedWith(reason);
    }
}
