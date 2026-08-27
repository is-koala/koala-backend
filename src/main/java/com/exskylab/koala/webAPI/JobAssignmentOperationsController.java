package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.CancellationMessages;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.dtos.cancellation.request.CancelAssignmentDto;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.core.dtos.dispute.request.OpenDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import com.exskylab.koala.core.mappers.CancellationMapper;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.mappers.TimesheetMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/job-assignments")
public class JobAssignmentOperationsController {

    private final CancellationService cancellationService;
    private final DisputeService disputeService;
    private final TimesheetService timesheetService;
    private final CancellationMapper cancellationMapper;
    private final DisputeMapper disputeMapper;
    private final TimesheetMapper timesheetMapper;

    public JobAssignmentOperationsController(CancellationService cancellationService,
                                             DisputeService disputeService,
                                             TimesheetService timesheetService,
                                             CancellationMapper cancellationMapper,
                                             DisputeMapper disputeMapper,
                                             TimesheetMapper timesheetMapper) {
        this.cancellationService = cancellationService;
        this.disputeService = disputeService;
        this.timesheetService = timesheetService;
        this.cancellationMapper = cancellationMapper;
        this.disputeMapper = disputeMapper;
        this.timesheetMapper = timesheetMapper;
    }

    @PostMapping("/{assignmentId}/cancel")
    public ResponseEntity<SuccessDataResult<CancellationDto>> cancel(
            @PathVariable UUID assignmentId, @RequestBody @Valid CancelAssignmentDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                cancellationMapper.toCancellationDto(
                        cancellationService.cancelAssignment(assignmentId, request.getReason())),
                CancellationMessages.CREATED, HttpStatus.CREATED));
    }

    @GetMapping("/{assignmentId}/cancellation")
    public ResponseEntity<SuccessDataResult<CancellationDto>> cancellation(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                cancellationService.getForAssignment(assignmentId)
                        .map(cancellationMapper::toCancellationDto).orElse(null),
                CancellationMessages.FETCHED, HttpStatus.OK));
    }

    @PostMapping("/{assignmentId}/disputes")
    public ResponseEntity<SuccessDataResult<DisputeDto>> openDispute(
            @PathVariable UUID assignmentId, @RequestBody @Valid OpenDisputeDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                disputeMapper.toDisputeDto(disputeService.openDispute(assignmentId, request.getReason())),
                DisputeMessages.OPENED, HttpStatus.CREATED));
    }

    @GetMapping("/{assignmentId}/disputes")
    public ResponseEntity<SuccessDataResult<List<DisputeDto>>> disputes(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toDisputeDtoList(disputeService.getForAssignment(assignmentId)),
                DisputeMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/{assignmentId}/check-in")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkIn(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkIn(assignmentId, point.getLatitude(), point.getLongitude())),
                TimesheetMessages.CHECKED_IN, HttpStatus.CREATED));
    }

    @PostMapping("/{assignmentId}/check-out")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkOut(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.ok(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkOut(assignmentId, point.getLatitude(), point.getLongitude())),
                TimesheetMessages.CHECKED_OUT, HttpStatus.OK));
    }

    @GetMapping("/{assignmentId}/work-sessions")
    public ResponseEntity<SuccessDataResult<List<WorkSessionDto>>> sessions(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDtoList(timesheetService.getSessions(assignmentId)),
                TimesheetMessages.LISTED, HttpStatus.OK));
    }
}
