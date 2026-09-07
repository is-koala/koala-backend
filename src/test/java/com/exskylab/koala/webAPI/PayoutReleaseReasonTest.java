package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.core.dtos.payout.request.ReleaseRequestDto;
import com.exskylab.koala.core.mappers.PayoutMapper;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutReleaseReasonTest {

    private StaffPayoutService staffPayoutService;
    private PayoutsController controller;

    private final UUID assignmentId = UUID.randomUUID();

    private final StaffPrincipal officer = new StaffPrincipal(UUID.randomUUID(), "finans@iskoala.com",
            "Finans Yetkilisi", Set.of("FINANCE_OFFICER"), Set.of(StaffPermission.PAYOUT_RELEASE), false);

    @BeforeEach
    void setUp() {
        staffPayoutService = mock(StaffPayoutService.class);
        PayoutMapper payoutMapper = mock(PayoutMapper.class);

        when(staffPayoutService.releaseToWorker(any(), any(), any())).thenReturn(new Payout());

        controller = new PayoutsController(mock(PayoutService.class), staffPayoutService, payoutMapper,
                mock(SecurityService.class));

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void theOperatorsReasonForAStandaloneReleaseReachesTheMoneyFacade() {
        controller.release(assignmentId, new ReleaseRequestDto("emanet elle çözüldü"), officer);

        verify(staffPayoutService).releaseToWorker(officer, assignmentId, "emanet elle çözüldü");
    }

    @Test
    void aReleaseWithoutABodyStillReleases() {
        controller.release(assignmentId, null, officer);

        verify(staffPayoutService).releaseToWorker(officer, assignmentId, null);
    }
}
