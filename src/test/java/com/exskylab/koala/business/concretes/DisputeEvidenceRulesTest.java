package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.Checksums;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.DisputeEvidenceDao;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.entities.DisputeStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeEvidenceRulesTest {

    private static final String STORED_KEY = "files/dispute-evidence/2f0b8f2e-1f14-4e01-9a5c-9d0d3a0f7f11.png";

    private DisputeDao disputeDao;
    private DisputeEvidenceDao disputeEvidenceDao;
    private StorageService storageService;
    private SecurityService securityService;
    private JobAssignmentService jobAssignmentService;
    private UploadProperties uploadProperties;
    private DisputeManager disputeManager;

    private final User employer = user();
    private final User worker = user();
    private final User stranger = user();

    @BeforeEach
    void setUp() {
        disputeDao = mock(DisputeDao.class);
        disputeEvidenceDao = mock(DisputeEvidenceDao.class);
        storageService = mock(StorageService.class);
        securityService = mock(SecurityService.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        uploadProperties = new UploadProperties();

        UserService userService = mock(UserService.class);
        when(userService.getReference(any())).thenAnswer(invocation -> {
            User reference = new User();
            reference.setId(invocation.getArgument(0));
            return reference;
        });

        AssignmentParticipants participants = new AssignmentParticipants(mock(CompanyContactService.class));

        disputeManager = new DisputeManager(disputeDao, disputeEvidenceDao, storageService,
                new UploadValidator(uploadProperties), uploadProperties, jobAssignmentService, userService,
                securityService, participants,
                new StaffPayoutManager(mock(PayoutService.class)),
                mock(com.exskylab.koala.business.abstracts.StaffAccountService.class),
                mock(ActivityNotificationService.class), new PageableSanitizer(),
                mock(com.exskylab.koala.core.mappers.DisputeMapper.class));

        when(storageService.uploadFile(any(), any(), any(), eq(FolderType.DISPUTE_EVIDENCE))).thenReturn(STORED_KEY);
        when(disputeEvidenceDao.save(any())).thenAnswer(invocation -> {
            DisputeEvidence saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static User user() {
        User created = new User();
        created.setId(UUID.randomUUID());
        return created;
    }

    private Dispute dispute(DisputeStatus status) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);

        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(worker);

        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setJobAssignment(assignment);
        dispute.setStatus(status);

        when(disputeDao.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeDao.findByIdForUpdate(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeDao.existsById(dispute.getId())).thenReturn(true);
        when(jobAssignmentService.getDetailedById(assignment.getId())).thenReturn(assignment);
        return dispute;
    }

    private static MockMultipartFile png() throws IOException {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "kanit.png", "image/png", out.toByteArray());
    }

    @Test
    void aStrangerCannotAttachEvidence() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> disputeManager.attachEvidence(dispute.getId(), png()));

        verify(storageService, never()).uploadFile(any(), any(), any(), any());
        verify(disputeEvidenceDao, never()).save(any());
    }

    @Test
    void aStrangerCannotListEvidence() {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class, () -> disputeManager.getEvidence(dispute.getId()));

        verify(disputeEvidenceDao, never()).findAllByDisputeIdOrderByCreatedAtAsc(any());
    }

    @Test
    void aStrangerCannotDownloadEvidence() {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setStorageKey(STORED_KEY);
        evidence.setUploadedBy(worker);
        when(disputeEvidenceDao.findByIdAndDisputeId(evidence.getId(), dispute.getId()))
                .thenReturn(Optional.of(evidence));
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> disputeManager.getEvidenceForDownload(dispute.getId(), evidence.getId()));
    }

    @Test
    void aPartyOfTheAssignmentCanAttachAndTheStoredRowNamesTheObjectAndItsChecksum() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        MockMultipartFile file = png();

        DisputeEvidence saved = disputeManager.attachEvidence(dispute.getId(), file);

        assertEquals(STORED_KEY, saved.getStorageKey());
        assertEquals("kanit.png", saved.getOriginalFileName());
        assertEquals(file.getBytes().length, saved.getFileSize());
        assertEquals(Checksums.sha256Hex(file.getBytes()), saved.getChecksumSha256());
        assertEquals(worker.getId(), saved.getUploadedBy().getId());
    }

    @Test
    void aResolvedDisputeAcceptsNoMoreEvidence() throws IOException {
        Dispute dispute = dispute(DisputeStatus.RESOLVED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ConflictException.class, () -> disputeManager.attachEvidence(dispute.getId(), png()));

        verify(storageService, never()).uploadFile(any(), any(), any(), any());
        verify(disputeEvidenceDao, never()).save(any());
    }

    @Test
    void theEvidenceLimitPerDisputeIsEnforcedBeforeAnythingIsStored() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(disputeEvidenceDao.countByDisputeId(dispute.getId()))
                .thenReturn((long) uploadProperties.getMaxEvidencePerDispute());

        assertThrows(ConflictException.class, () -> disputeManager.attachEvidence(dispute.getId(), png()));

        verify(storageService, never()).uploadFile(any(), any(), any(), any());
        verify(disputeEvidenceDao, never()).save(any());
    }

    @Test
    void aFailedObjectStoreWriteLeavesTheDisputeAndItsExistingEvidenceUntouched() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(storageService.uploadFile(any(), any(), any(), eq(FolderType.DISPUTE_EVIDENCE)))
                .thenThrow(new ValidationException("{storage.upload.failed}"));

        assertThrows(ValidationException.class, () -> disputeManager.attachEvidence(dispute.getId(), png()));

        assertEquals(DisputeStatus.OPEN, dispute.getStatus());
        verify(disputeEvidenceDao, never()).save(any());
    }

    @Test
    void anObjectWrittenBeforeAFailedCommitIsRemovedWhenTheTransactionRollsBack() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        doThrow(new IllegalStateException("constraint")).when(disputeEvidenceDao).save(any());

        TransactionSynchronizationManager.initSynchronization();
        assertThrows(IllegalStateException.class, () -> disputeManager.attachEvidence(dispute.getId(), png()));

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storageService).deleteFile(STORED_KEY);
    }

    @Test
    void aCommittedAttachmentKeepsItsObject() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        TransactionSynchronizationManager.initSynchronization();
        disputeManager.attachEvidence(dispute.getId(), png());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(storageService, never()).deleteFile(any());
    }

    @Test
    void anAttachmentWithAnUnknownTransactionOutcomeKeepsItsObject() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        TransactionSynchronizationManager.initSynchronization();
        disputeManager.attachEvidence(dispute.getId(), png());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));

        verify(storageService, never()).deleteFile(any());
    }

    @Test
    void aFileThatIsNotWhatItsExtensionClaimsIsRejectedBeforeAnythingIsStored() {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        MockMultipartFile disguised = new MockMultipartFile("file", "kanit.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThrows(ValidationException.class, () -> disputeManager.attachEvidence(dispute.getId(), disguised));

        verify(storageService, never()).uploadFile(any(), any(), any(), any());
    }

    @Test
    void anExecutableIsRejectedByTheExtensionWhitelist() {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        MockMultipartFile executable = new MockMultipartFile("file", "kanit.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A, 0x00});

        assertThrows(ValidationException.class, () -> disputeManager.attachEvidence(dispute.getId(), executable));

        verify(storageService, never()).uploadFile(any(), any(), any(), any());
    }

    @Test
    void theOriginalFileNameNeverBecomesPartOfTheStorageKey() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        disputeManager.attachEvidence(dispute.getId(), png());

        verify(storageService).uploadFile(any(), eq("kanit.png"), eq("image/png"), eq(FolderType.DISPUTE_EVIDENCE));
    }

    @Test
    void staffReadsNeedNoMarketplaceIdentity() {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setStorageKey(STORED_KEY);
        evidence.setUploadedBy(worker);
        when(disputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc(dispute.getId()))
                .thenReturn(List.of(evidence));
        when(disputeEvidenceDao.findByIdAndDisputeId(evidence.getId(), dispute.getId()))
                .thenReturn(Optional.of(evidence));

        assertEquals(1, disputeManager.getEvidenceForStaff(dispute.getId()).size());
        assertEquals(STORED_KEY,
                disputeManager.getEvidenceForStaffDownload(dispute.getId(), evidence.getId()).getStorageKey());

        verify(securityService, never()).getAuthenticatedUserId();
        verify(securityService, never()).getAuthenticatedUser();
    }

    @Test
    void theStoredBytesAreTheBytesThatWereUploaded() throws IOException {
        Dispute dispute = dispute(DisputeStatus.OPEN);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        MockMultipartFile file = png();

        disputeManager.attachEvidence(dispute.getId(), file);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).uploadFile(stored.capture(), any(), any(), any());
        assertArrayEquals(file.getBytes(), stored.getValue());
    }
}
