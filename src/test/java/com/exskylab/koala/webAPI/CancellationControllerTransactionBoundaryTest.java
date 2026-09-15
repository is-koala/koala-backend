package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.IdentityRevealService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.mappers.CancellationMapper;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.mappers.IdentityRevealMapper;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.mappers.TimesheetMapper;
import com.exskylab.koala.entities.Cancellation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancellationControllerTransactionBoundaryTest {

    @Test
    void cancellationResponseDoesNotQueryThePayoutAggregateAfterTheMutation() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        Cancellation cancellation = new Cancellation();
        CancellationService cancellationService = mock(CancellationService.class);
        CancellationMapper mapper = mock(CancellationMapper.class);
        when(cancellationService.cancelAssignment(assignmentId, "vazgeçtim")).thenReturn(cancellation);
        JobAssignmentOperationsController controller = new JobAssignmentOperationsController(
                cancellationService, mock(DisputeService.class), mock(TimesheetService.class),
                mock(JobAssignmentService.class), mapper,
                mock(DisputeMapper.class), mock(TimesheetMapper.class), mock(IdentityRevealService.class),
                mock(IdentityRevealMapper.class), mock(ClientIpResolver.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/job-assignments/{assignmentId}/cancel", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"vazgeçtim\"}"))
                .andExpect(status().isCreated());

        verify(mapper).toCancellationDto(cancellation);
        assertFalse(Arrays.stream(JobAssignmentOperationsController.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(PayoutService.class)));
    }
}
