package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffPayoutFacadeTest {

    private PayoutService payoutService;
    private StaffPayoutManager facade;

    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        payoutService = mock(PayoutService.class);
        facade = new StaffPayoutManager(payoutService);
    }

    private static StaffPrincipal staff(StaffPermission... permissions) {
        return new StaffPrincipal(UUID.randomUUID(), "staff@iskoala.com", "Personel",
                Set.of("FINANCE_OFFICER"), Set.of(permissions), false);
    }

    @Test
    void staffWithoutTheReleasePermissionNeverReachesThePayoutService() {
        StaffPrincipal analyst = staff(StaffPermission.FINANCE_READ);

        assertThrows(ForbiddenException.class, () -> facade.releaseToWorker(analyst, assignmentId, "sebep"));
        verifyNoInteractions(payoutService);
    }

    @Test
    void staffWithoutTheRefundPermissionNeverReachesThePayoutService() {
        StaffPrincipal officer = staff(StaffPermission.PAYOUT_RELEASE);

        assertThrows(ForbiddenException.class,
                () -> facade.refundEmployer(officer, assignmentId, BigDecimal.TEN, "sebep"));
        verifyNoInteractions(payoutService);
    }

    @Test
    void staffWithoutAStaffRecordCannotMoveMoney() {
        StaffPrincipal stranger = StaffPrincipal.unregistered("sub", Set.of("FINANCE_OFFICER"));

        assertThrows(ForbiddenException.class, () -> facade.releaseToWorker(stranger, assignmentId, "sebep"));
        verifyNoInteractions(payoutService);
    }

    @Test
    void staffHoldingThePermissionReachesTheUnguardedInnerMethod() {
        when(payoutService.releaseToWorker(any())).thenReturn(new Payout());

        facade.releaseToWorker(staff(StaffPermission.PAYOUT_RELEASE), assignmentId, "sebep");

        verify(payoutService, times(1)).releaseToWorker(assignmentId);
    }

    @Test
    void noActorReleasesNoMoneyNowThatTheMarketplaceBridgeIsCut() {
        assertThrows(ForbiddenException.class, () -> facade.releaseToWorker(null, assignmentId, "sebep"));
        verifyNoInteractions(payoutService);
    }

    @Test
    void noActorRefundsNoMoneyNowThatTheMarketplaceBridgeIsCut() {
        assertThrows(ForbiddenException.class,
                () -> facade.refundEmployer(null, assignmentId, BigDecimal.TEN, "sebep"));
        verifyNoInteractions(payoutService);
    }

    @Test
    void noActorPassesTheBareRequirePermissionGateEither() {
        assertThrows(ForbiddenException.class,
                () -> facade.requirePermission(null, StaffPermission.PAYOUT_RELEASE, assignmentId));
        verifyNoInteractions(payoutService);
    }

    @Test
    void staffHoldingThePermissionIsUnaffectedByTheCutover() {
        when(payoutService.releaseToWorker(any())).thenReturn(new Payout());

        facade.releaseToWorker(staff(StaffPermission.PAYOUT_RELEASE), assignmentId, "sebep");

        verify(payoutService, times(1)).releaseToWorker(assignmentId);
    }

    @Test
    void aFinanceAnalystWithoutTheReconcilePermissionNeverReadsTheSettlementQueue() {
        StaffPrincipal analyst = staff(StaffPermission.FINANCE_READ, StaffPermission.PAYMENT_READ);

        assertThrows(ForbiddenException.class,
                () -> facade.listForStaff(analyst, List.of(PayoutStatus.PROCESSING), PageRequest.of(0, 20)));
        verifyNoInteractions(payoutService);
    }

    @Test
    void noActorReadsNoSettlementQueue() {
        assertThrows(ForbiddenException.class,
                () -> facade.listForStaff(null, List.of(), PageRequest.of(0, 20)));
        verifyNoInteractions(payoutService);
    }

    @Test
    void staffHoldingTheReconcilePermissionReadsTheQueueThroughTheInnerService() {
        PageDto<StaffPayoutDto> queue = PageDto.of(Page.empty(), List.of());
        Pageable requested = PageRequest.of(0, 20);
        List<PayoutStatus> statuses = List.of(PayoutStatus.PROCESSING);
        when(payoutService.listForStaff(statuses, requested)).thenReturn(queue);

        PageDto<StaffPayoutDto> result = facade.listForStaff(staff(StaffPermission.PAYOUT_RECONCILE), statuses, requested);

        assertSame(queue, result);
        verify(payoutService, times(1)).listForStaff(statuses, requested);
    }
}
