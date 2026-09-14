package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeResolution;
import com.exskylab.koala.entities.DisputeStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PayoutReleaseOrigin;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffMember;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DisputeResolutionAuthorizationTest {

    private DisputeDao disputeDao;
    private PayoutService payoutService;
    private SecurityService securityService;
    private StaffAccountService staffAccountService;
    private JobAssignmentService jobAssignmentService;
    private DisputeManager disputeManager;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();

    private Dispute dispute;

    @BeforeEach
    void setUp() {
        disputeDao = mock(DisputeDao.class);
        payoutService = mock(PayoutService.class);
        securityService = mock(SecurityService.class);
        staffAccountService = mock(StaffAccountService.class);
        jobAssignmentService = mock(JobAssignmentService.class);

        disputeManager = new DisputeManager(disputeDao, mock(com.exskylab.koala.dataAccess.DisputeEvidenceDao.class),
                mock(com.exskylab.koala.core.utilities.storage.StorageService.class),
                new com.exskylab.koala.core.helpers.UploadValidator(
                        new com.exskylab.koala.core.properties.UploadProperties()),
                new com.exskylab.koala.core.properties.UploadProperties(),
                jobAssignmentService, mock(UserService.class),
                securityService, mock(AssignmentParticipants.class), new StaffPayoutManager(payoutService),
                staffAccountService,
                mock(ActivityNotificationService.class), new PageableSanitizer(),
                mock(com.exskylab.koala.core.mappers.DisputeMapper.class));

        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);

        dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setJobAssignment(assignment);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputeDao.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeDao.findJobAssignmentIdById(disputeId)).thenReturn(Optional.of(assignmentId));
        when(disputeDao.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(jobAssignmentService.getDetailedByIdForUpdate(assignmentId)).thenReturn(assignment);
        when(disputeDao.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getAuthenticatedUser()).thenReturn(new User());
        when(staffAccountService.getReference(any())).thenAnswer(invocation -> {
            StaffMember member = new StaffMember();
            member.setId(invocation.getArgument(0));
            return member;
        });
    }

    private static StaffPrincipal staff(StaffPermission... permissions) {
        return new StaffPrincipal(UUID.randomUUID(), "personel@iskoala.com", "Uyuşmazlık Uzmanı",
                Set.of("DISPUTE_HANDLER"), Set.of(permissions), false);
    }

    @Test
    void aDisputeHandlerWithoutTheReleasePermissionCannotSendEscrowToTheWorker() {
        StaffPrincipal handler = staff(StaffPermission.DISPUTE_RESOLVE);

        assertThrows(ForbiddenException.class, () -> disputeManager.resolveDispute(
                handler, disputeId, DisputeResolution.RELEASE_TO_WORKER, "işçi haklı"));

        verifyNoInteractions(payoutService);
        assertEquals(DisputeStatus.OPEN, dispute.getStatus());
    }

    @Test
    void aDisputeHandlerWithoutTheRefundPermissionCannotRefundTheEmployer() {
        StaffPrincipal handler = staff(StaffPermission.DISPUTE_RESOLVE);

        assertThrows(ForbiddenException.class, () -> disputeManager.resolveDispute(
                handler, disputeId, DisputeResolution.REFUND_EMPLOYER, "işveren haklı"));

        verifyNoInteractions(payoutService);
        assertEquals(DisputeStatus.OPEN, dispute.getStatus());
    }

    @Test
    void aDisputeHandlerMayStillDismissWithoutAnyFinancialPermission() {
        StaffPrincipal handler = staff(StaffPermission.DISPUTE_RESOLVE);

        disputeManager.resolveDispute(handler, disputeId, DisputeResolution.DISMISSED, "dayanaksız");

        verifyNoInteractions(payoutService);
        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus());
        assertEquals(handler.staffId(), dispute.getResolvedByStaff().getId(),
                "Personel karari staff_members yabanci anahtariyla damgalanmali.");
    }

    @Test
    void resolutionLocksTheAssignmentBeforeReadingTheDisputeForUpdate() {
        StaffPrincipal handler = staff(StaffPermission.DISPUTE_RESOLVE);

        disputeManager.resolveDispute(handler, disputeId, DisputeResolution.DISMISSED, "dayanaksız");

        var order = inOrder(disputeDao, jobAssignmentService);
        order.verify(disputeDao).findJobAssignmentIdById(disputeId);
        order.verify(jobAssignmentService).getDetailedByIdForUpdate(assignmentId);
        order.verify(disputeDao).findByIdForUpdate(disputeId);
    }

    @Test
    void staffHoldingTheReleasePermissionResolvesAndMovesTheMoney() {
        StaffPrincipal owner = staff(StaffPermission.DISPUTE_RESOLVE, StaffPermission.PAYOUT_RELEASE);

        disputeManager.resolveDispute(owner, disputeId, DisputeResolution.RELEASE_TO_WORKER, "işçi haklı");

        verify(payoutService, times(1)).releaseToWorker(assignmentId, PayoutReleaseOrigin.DISPUTE);
        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus());
    }

    @Test
    void staffHoldingTheRefundPermissionResolvesAndRefundsTheEmployer() {
        StaffPrincipal owner = staff(StaffPermission.DISPUTE_RESOLVE, StaffPermission.PAYMENT_REFUND);

        disputeManager.resolveDispute(owner, disputeId, DisputeResolution.REFUND_EMPLOYER, "işveren haklı");

        verify(payoutService, times(1)).refundEmployer(assignmentId, null, "işveren haklı");
        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus());
    }

    @Test
    void theFinancialPermissionIsCheckedBeforeTheAlreadyResolvedState() {
        dispute.setStatus(DisputeStatus.RESOLVED);
        StaffPrincipal handler = staff(StaffPermission.DISPUTE_RESOLVE);

        assertThrows(ForbiddenException.class, () -> disputeManager.resolveDispute(
                handler, disputeId, DisputeResolution.RELEASE_TO_WORKER, "işçi haklı"));

        verifyNoInteractions(payoutService);
    }

    @Test
    void anAlreadyResolvedDisputeStillConflictsForStaffThatMayMoveMoney() {
        dispute.setStatus(DisputeStatus.RESOLVED);
        StaffPrincipal owner = staff(StaffPermission.DISPUTE_RESOLVE, StaffPermission.PAYOUT_RELEASE);

        assertThrows(ConflictException.class, () -> disputeManager.resolveDispute(
                owner, disputeId, DisputeResolution.RELEASE_TO_WORKER, "işçi haklı"));

        verifyNoInteractions(payoutService);
    }

    @Test
    void theMarketplaceBridgeNoLongerResolvesDisputesNowThatItIsCut() {
        assertThrows(ForbiddenException.class, () -> disputeManager.resolveDispute(
                null, disputeId, DisputeResolution.RELEASE_TO_WORKER, "işçi haklı"));

        verifyNoInteractions(payoutService);
        assertEquals(DisputeStatus.OPEN, dispute.getStatus());
    }

    @Test
    void everyMoneyMovingResolutionDemandsAFinancialAndMutatingPermission() {
        for (DisputeResolution resolution : DisputeResolution.values()) {
            if (!resolution.movesMoney()) {
                continue;
            }
            assertEquals(PermissionSensitivity.FINANCIAL, resolution.getMoneyPermission().getSensitivity(),
                    resolution + " para hareket ettiriyor, izni FINANCIAL olmalı");
            assertTrue(resolution.getMoneyPermission().isMutating(),
                    resolution + " para hareket ettiriyor, izni yazma izni olmalı");
        }

        assertTrue(DisputeResolution.RELEASE_TO_WORKER.movesMoney());
        assertTrue(DisputeResolution.REFUND_EMPLOYER.movesMoney());
        assertFalse(DisputeResolution.DISMISSED.movesMoney());
    }
}
