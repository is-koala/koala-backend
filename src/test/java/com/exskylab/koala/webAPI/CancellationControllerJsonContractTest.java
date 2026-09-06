package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.core.helpers.PlainDecimalStringConverter;
import com.exskylab.koala.core.mappers.CancellationMapper;
import com.exskylab.koala.core.mappers.CancellationMapperImpl;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.mappers.TimesheetMapper;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancellationControllerJsonContractTest {

    @Test
    void previewMoneyIsSerializedAsExactPlainStrings() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        CancellationService cancellationService = mock(CancellationService.class);
        when(cancellationService.previewCancellation(assignmentId)).thenReturn(
                new CancellationPreviewDto(
                        PlainDecimalStringConverter.convert(new BigDecimal("0.5000")),
                        PlainDecimalStringConverter.convert(new BigDecimal("1E+3")),
                        PlainDecimalStringConverter.convert(new BigDecimal("1050.11")), 120, true));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(cancellationService)).build();

        mockMvc.perform(get("/job-assignments/{assignmentId}/cancellation-preview", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeRate").value("0.5000"))
                .andExpect(jsonPath("$.data.feeAmount").value("1000"))
                .andExpect(jsonPath("$.data.refundAmount").value("1050.11"));
    }

    @Test
    void cancellationMoneyIsSerializedAsExactPlainStrings() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);
        Refund refund = new Refund();
        refund.setStatus(RefundStatus.PENDING);
        Cancellation cancellation = new Cancellation();
        cancellation.setId(UUID.randomUUID());
        cancellation.setJobAssignment(assignment);
        cancellation.setCancelledByParty(CancellationParty.EMPLOYER);
        cancellation.setNoticeMinutes(120);
        cancellation.setFeeRate(new BigDecimal("0.0750"));
        cancellation.setFeeAmount(new BigDecimal("1E+3"));
        cancellation.setRefundAmount(new BigDecimal("1050.11"));
        cancellation.setCancelledAt(Instant.parse("2026-09-06T12:30:00Z"));
        cancellation.setRefund(refund);
        CancellationService cancellationService = mock(CancellationService.class);
        when(cancellationService.getForAssignment(assignmentId)).thenReturn(Optional.of(cancellation));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                controller(cancellationService, new CancellationMapperImpl())).build();

        mockMvc.perform(get("/job-assignments/{assignmentId}/cancellation", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeRate").value("0.0750"))
                .andExpect(jsonPath("$.data.feeAmount").value("1000"))
                .andExpect(jsonPath("$.data.refundAmount").value("1050.11"))
                .andExpect(jsonPath("$.data.refundStatus").value("PENDING"));
    }

    private static JobAssignmentOperationsController controller(CancellationService cancellationService) {
        return controller(cancellationService, mock(CancellationMapper.class));
    }

    private static JobAssignmentOperationsController controller(CancellationService cancellationService,
                                                                 CancellationMapper cancellationMapper) {
        return new JobAssignmentOperationsController(cancellationService, mock(DisputeService.class),
                mock(TimesheetService.class), mock(JobAssignmentService.class),
                mock(JobAssignmentMapper.class), cancellationMapper, mock(DisputeMapper.class),
                mock(TimesheetMapper.class));
    }
}
