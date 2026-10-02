package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminTimelineEventDto;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.dtos.transport.request.SeatBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleStopWriteDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleWriteDto;
import com.exskylab.koala.core.dtos.transport.request.TransportPlanWriteDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingQrDto;
import com.exskylab.koala.core.dtos.transport.response.JobTransportDto;
import com.exskylab.koala.core.dtos.transport.response.PublicShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleBoardingRowDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleStopDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.BoardingTokens;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.ConstraintViolations;
import com.exskylab.koala.core.helpers.DisplayNames;
import com.exskylab.koala.core.helpers.GeoDistance;
import com.exskylab.koala.core.helpers.JobAddressVisibility;
import com.exskylab.koala.core.helpers.JobEditors;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.helpers.TimesheetEventPolicy;
import com.exskylab.koala.core.mappers.TransportMapper;
import com.exskylab.koala.core.properties.TransportBoardingProperties;
import com.exskylab.koala.core.properties.TransportReminderProperties;
import com.exskylab.koala.dataAccess.BoardingEventDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.SeatAssignmentDao;
import com.exskylab.koala.dataAccess.ShuttleDao;
import com.exskylab.koala.dataAccess.ShuttleStopDao;
import com.exskylab.koala.dataAccess.TransportPlanDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.BoardingEvent;
import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.SeatAssignment;
import com.exskylab.koala.entities.Shuttle;
import com.exskylab.koala.entities.ShuttleStop;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import com.exskylab.koala.entities.TransportDirection;
import com.exskylab.koala.entities.TransportMethod;
import com.exskylab.koala.entities.TransportPlan;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransportManager implements TransportService {

    private static final Set<AssignmentStatus> SEAT_STATUSES = EnumSet.of(AssignmentStatus.CONFIRMED,
            AssignmentStatus.IN_PROGRESS);

    private static final Set<AssignmentStatus> VIEW_STATUSES = EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION,
            AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED);

    private static final Set<AssignmentStatus> NOTIFIED_STATUSES = EnumSet.of(AssignmentStatus.CONFIRMED,
            AssignmentStatus.IN_PROGRESS);

    private static final Set<AssignmentStatus> BOARDING_LIST_STATUSES = EnumSet.of(AssignmentStatus.CONFIRMED,
            AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED);

    private static final Set<BoardingKind> SETTLED_KINDS = EnumSet.of(BoardingKind.BOARDED, BoardingKind.NO_SHOW);

    private static final Locale REMINDER_LOCALE = Locale.of("tr");

    private static final DateTimeFormatter WALL_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String PUSH_TYPE = "TRANSPORT";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TransportPlanDao transportPlanDao;
    private final ShuttleDao shuttleDao;
    private final ShuttleStopDao shuttleStopDao;
    private final SeatAssignmentDao seatAssignmentDao;
    private final JobDao jobDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final SecurityService securityService;
    private final JobEditors jobEditors;
    private final AddressService addressService;
    private final AssignmentParticipants participants;
    private final NotificationService notificationService;
    private final MessageResolver messageResolver;
    private final TransportMapper transportMapper;
    private final TransportReminderProperties reminderProperties;
    private final BoardingEventDao boardingEventDao;
    private final UserService userService;
    private final Clock clock;
    private final TimesheetEventPolicy timesheetEventPolicy;
    private final TransportBoardingProperties boardingProperties;
    private final JobAddressVisibility addressVisibility = new JobAddressVisibility();

    public TransportManager(TransportPlanDao transportPlanDao, ShuttleDao shuttleDao, ShuttleStopDao shuttleStopDao,
                            SeatAssignmentDao seatAssignmentDao, JobDao jobDao, JobAssignmentDao jobAssignmentDao,
                            SecurityService securityService, JobEditors jobEditors,
                            AddressService addressService, AssignmentParticipants participants,
                            NotificationService notificationService, MessageResolver messageResolver,
                            TransportMapper transportMapper, TransportReminderProperties reminderProperties,
                            BoardingEventDao boardingEventDao, UserService userService, Clock clock,
                            TimesheetEventPolicy timesheetEventPolicy, TransportBoardingProperties boardingProperties) {
        this.transportPlanDao = transportPlanDao;
        this.shuttleDao = shuttleDao;
        this.shuttleStopDao = shuttleStopDao;
        this.seatAssignmentDao = seatAssignmentDao;
        this.jobDao = jobDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.securityService = securityService;
        this.jobEditors = jobEditors;
        this.addressService = addressService;
        this.participants = participants;
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
        this.transportMapper = transportMapper;
        this.reminderProperties = reminderProperties;
        this.boardingEventDao = boardingEventDao;
        this.userService = userService;
        this.clock = clock;
        this.timesheetEventPolicy = timesheetEventPolicy;
        this.boardingProperties = boardingProperties;
    }

    @Override
    @Transactional
    public TransportPlanDto writePlan(UUID jobId, TransportPlanWriteDto request) {
        Job job = requireEditor(jobId);
        UUID actorId = securityService.getAuthenticatedUserId();

        TransportPlan plan = transportPlanDao.findByJobId(jobId).orElseGet(() -> {
            TransportPlan created = new TransportPlan();
            created.setJob(job);
            return created;
        });
        plan.setOutboundMethod(request.getOutboundMethod());
        plan.setReturnMethod(request.getReturnMethod());
        plan.setAllowanceAmount(request.getAllowanceAmount());
        plan.setAllowanceNote(blankToNull(request.getAllowanceNote()));
        plan.setNotes(blankToNull(request.getNotes()));

        Rewrite rewrite = new Rewrite();
        Reconciled<Shuttle> shuttles = reconcile(plan.getShuttles(), Shuttle::getId,
                request.getShuttles() == null ? List.of() : request.getShuttles(), ShuttleWriteDto::getId,
                () -> newShuttle(plan),
                shuttleId -> Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                        "shuttle is not part of the job's transport plan"),
                TransportMessages.SHUTTLE_DUPLICATE,
                (shuttle, dto, order) -> applyShuttle(shuttle, dto, order, actorId, rewrite), rewrite);

        rewrite.removedShuttleIds.addAll(shuttles.removedIds());
        Set<UUID> droppedAssignmentIds = dropSeats(rewrite.removedShuttleIds, rewrite.removedStopIds);
        rewrite.collectionEdits.forEach(Runnable::run);
        TransportPlan saved = transportPlanDao.save(plan);
        transportPlanDao.flush();
        rejectCapacityBelowSeats(rewrite.requestedCapacity);
        resetReminders(rewrite.rescheduledStopIds);

        log.info("Transport plan {} for job {} written by user {}: {} shuttles kept or added, {} removed",
                saved.getId(), jobId, actorId, shuttles.ordered().size(), rewrite.removedShuttleIds.size());
        notifyPlanUpdated(job, droppedAssignmentIds);
        return planDto(saved);
    }

    private static final class Rewrite {
        private final Set<UUID> removedShuttleIds = new HashSet<>();
        private final Set<UUID> removedStopIds = new HashSet<>();
        private final Set<UUID> rescheduledStopIds = new HashSet<>();
        private final Map<UUID, Integer> requestedCapacity = new HashMap<>();
        private final List<Runnable> collectionEdits = new ArrayList<>();
    }

    private Shuttle newShuttle(TransportPlan plan) {
        Shuttle shuttle = new Shuttle();
        shuttle.setPlan(plan);
        shuttle.setQrSecret(newSecret());
        return shuttle;
    }

    private void applyShuttle(Shuttle shuttle, ShuttleWriteDto dto, int order, UUID actorId, Rewrite rewrite) {
        if (shuttle.getId() != null) {
            if (shuttle.getDirection() != dto.getDirection()) {
                throw new ConflictException(TransportMessages.SHUTTLE_DIRECTION_LOCKED);
            }
            rewrite.requestedCapacity.put(shuttle.getId(), dto.getCapacity());
        }
        shuttle.setDirection(dto.getDirection());
        shuttle.setName(dto.getName().trim());
        shuttle.setPlate(blankToNull(dto.getPlate()));
        shuttle.setCapacity(dto.getCapacity());
        shuttle.setDriverName(blankToNull(dto.getDriverName()));
        shuttle.setDriverPhone(blankToNull(dto.getDriverPhone()));
        shuttle.setSortOrder(order);

        Reconciled<ShuttleStop> stops = reconcile(shuttle.getStops(), ShuttleStop::getId, dto.getStops(),
                ShuttleStopWriteDto::getId,
                () -> {
                    ShuttleStop stop = new ShuttleStop();
                    stop.setShuttle(shuttle);
                    return stop;
                },
                stopId -> Concealment.notFound(TransportMessages.STOP_NOT_FOUND, actorId, stopId,
                        "stop is not part of the shuttle"),
                TransportMessages.STOP_DUPLICATE,
                (stop, stopDto, stopOrder) -> applyStop(stop, stopDto, stopOrder, rewrite), rewrite);
        rewrite.removedStopIds.addAll(stops.removedIds());
    }

    private void applyStop(ShuttleStop stop, ShuttleStopWriteDto dto, int order, Rewrite rewrite) {
        if (stop.getId() != null && !dto.getScheduledAt().equals(stop.getScheduledAt())) {
            rewrite.rescheduledStopIds.add(stop.getId());
        }
        if (dto.getAddress() != null) {
            stop.setAddress(addressService.createAddress(dto.getAddress()));
        }
        stop.setLabel(blankToNull(dto.getLabel()));
        stop.setScheduledAt(dto.getScheduledAt());
        stop.setSortOrder(order);
    }

    private interface Apply<E, D> {
        void apply(E entity, D request, int order);
    }

    private record Reconciled<E>(List<E> ordered, Set<UUID> removedIds) {
    }

    private static <E, D> Reconciled<E> reconcile(List<E> current, Function<E, UUID> entityId, List<D> requested,
                                                  Function<D, UUID> requestedId, Supplier<E> create,
                                                  Function<UUID, ResourceNotFoundException> missing,
                                                  String duplicateKey, Apply<E, D> apply, Rewrite rewrite) {
        Map<UUID, E> existing = new LinkedHashMap<>();
        current.forEach(entity -> existing.put(entityId.apply(entity), entity));
        Set<UUID> kept = new HashSet<>();
        List<E> ordered = new ArrayList<>();
        List<E> added = new ArrayList<>();
        int order = 0;
        for (D dto : requested) {
            UUID id = requestedId.apply(dto);
            E entity;
            if (id == null) {
                entity = create.get();
                added.add(entity);
            } else {
                entity = existing.get(id);
                if (entity == null) {
                    throw missing.apply(id);
                }
                if (!kept.add(id)) {
                    throw new ValidationException(duplicateKey);
                }
            }
            apply.apply(entity, dto, order++);
            ordered.add(entity);
        }
        Set<UUID> removed = existing.keySet().stream()
                .filter(id -> !kept.contains(id))
                .collect(Collectors.toSet());
        rewrite.collectionEdits.add(() -> {
            current.removeIf(entity -> removed.contains(entityId.apply(entity)));
            current.addAll(added);
        });
        return new Reconciled<>(ordered, removed);
    }

    private Set<UUID> dropSeats(Set<UUID> removedShuttleIds, Set<UUID> removedStopIds) {
        Map<UUID, SeatAssignment> doomed = new LinkedHashMap<>();
        if (!removedShuttleIds.isEmpty()) {
            seatAssignmentDao.findAllByShuttleIdIn(removedShuttleIds).forEach(seat -> doomed.put(seat.getId(), seat));
        }
        if (!removedStopIds.isEmpty()) {
            seatAssignmentDao.findAllByStopIdIn(removedStopIds).forEach(seat -> doomed.put(seat.getId(), seat));
        }
        if (doomed.isEmpty()) {
            return Set.of();
        }
        Set<UUID> assignmentIds = doomed.values().stream()
                .map(seat -> seat.getJobAssignment().getId())
                .collect(Collectors.toSet());
        seatAssignmentDao.deleteAll(doomed.values());
        seatAssignmentDao.flush();
        log.info("{} seat assignments dropped because their shuttle or stop left the plan", doomed.size());
        return assignmentIds;
    }

    private void resetReminders(Set<UUID> rescheduledStopIds) {
        if (rescheduledStopIds.isEmpty()) {
            return;
        }
        List<SeatAssignment> seats = seatAssignmentDao.findAllByStopIdIn(rescheduledStopIds);
        seats.forEach(seat -> {
            seat.setEveningReminderSentAt(null);
            seat.setDepartureReminderSentAt(null);
            seat.setBoardingReminderSentAt(null);
            seat.setNotBoardedNoticeSentAt(null);
        });
        seatAssignmentDao.saveAll(seats);
    }

    private void rejectCapacityBelowSeats(Map<UUID, Integer> requestedCapacity) {
        if (requestedCapacity.isEmpty()) {
            return;
        }
        for (SeatAssignmentDao.ShuttleSeatCount count : seatAssignmentDao.countByShuttleIds(requestedCapacity.keySet())) {
            Integer capacity = requestedCapacity.get(count.getShuttleId());
            if (capacity != null && count.getTotal() > capacity) {
                log.warn("Shuttle {} capacity {} refused: {} seats already taken",
                        count.getShuttleId(), capacity, count.getTotal());
                throw new ConflictException(TransportMessages.SHUTTLE_CAPACITY_BELOW_SEATS);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransportPlanDto getPlan(UUID jobId) {
        requireEditor(jobId);
        return planDto(requirePlan(jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public TransportPlanDto getPlanForStaff(UUID jobId) {
        jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));
        return planDto(requirePlan(jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentTransportDto getAssignmentTransport(UUID assignmentId) {
        JobAssignment assignment = requireParty(assignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();
        TransportPlan plan = requirePlan(assignment.getJob().getId());
        List<SeatAssignment> seats = seatAssignmentDao.findAllByJobAssignmentIdIn(List.of(assignmentId));
        return assignmentView(plan, assignment, seats, actorId, load(List.of(plan)));
    }

    @Override
    @Transactional
    public AssignmentTransportDto selectSeat(UUID assignmentId, SeatSelectionDto request) {
        JobAssignment assignment = requireParty(assignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();
        if (!SEAT_STATUSES.contains(assignment.getStatus())) {
            throw new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
        TransportPlan plan = requirePlan(assignment.getJob().getId());
        Shuttle shuttle = requireShuttle(plan, request.getShuttleId(), actorId);
        ShuttleStop stop = requireStop(shuttle, request.getStopId(), actorId);

        SeatAssignment seat = seatAssignmentDao.findByJobAssignmentIdAndDirection(assignmentId, shuttle.getDirection())
                .orElse(null);
        boolean sameShuttle = seat != null && seat.getShuttle().getId().equals(shuttle.getId());
        if (!sameShuttle) {
            int capacity = shuttleDao.lockAndReadCapacity(shuttle.getId()).orElse(0);
            long taken = seatAssignmentDao.countByShuttleId(shuttle.getId());
            if (taken >= capacity) {
                log.info("Seat refused on shuttle {} for assignment {}: {} of {} seats taken",
                        shuttle.getId(), assignmentId, taken, capacity);
                throw new ConflictException(TransportMessages.SHUTTLE_FULL);
            }
        }
        if (seat == null) {
            seat = new SeatAssignment();
            seat.setJobAssignment(assignment);
            seat.setDirection(shuttle.getDirection());
        }
        boolean changed = !sameShuttle || !seat.getStop().getId().equals(stop.getId());
        seat.setShuttle(shuttle);
        seat.setStop(stop);
        if (changed) {
            seat.setEveningReminderSentAt(null);
            seat.setDepartureReminderSentAt(null);
            seat.setBoardingReminderSentAt(null);
            seat.setNotBoardedNoticeSentAt(null);
        }
        try {
            seatAssignmentDao.saveAndFlush(seat);
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.named(exception, "uk_seat_assignments_assignment_direction_active")) {
                log.info("Seat for assignment {} on direction {} was taken by a concurrent request",
                        assignmentId, shuttle.getDirection());
                throw new ConflictException(TransportMessages.SEAT_ALREADY_TAKEN);
            }
            throw exception;
        }
        log.info("Seat for assignment {} on direction {} set to shuttle {} stop {} by user {}",
                assignmentId, shuttle.getDirection(), shuttle.getId(), stop.getId(), actorId);

        List<SeatAssignment> seats = seatAssignmentDao.findAllByJobAssignmentIdIn(List.of(assignmentId));
        return assignmentView(plan, assignment, seats, actorId, load(List.of(plan)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobTransportDto> publicSummaryFor(UUID jobId) {
        return transportPlanDao.findByJobId(jobId).map(plan -> {
            JobTransportDto dto = transportMapper.toJobTransportDto(plan);
            Map<UUID, List<ShuttleStop>> stops = stopsByShuttle(plan.getShuttles());
            dto.setShuttleCount(plan.getShuttles().size());
            dto.setShuttles(plan.getShuttles().stream().map(shuttle -> {
                PublicShuttleDto publicShuttle = transportMapper.toPublicShuttleDto(shuttle);
                publicShuttle.setStops(stops.getOrDefault(shuttle.getId(), List.of()).stream()
                        .map(transportMapper::toPublicStopDto)
                        .toList());
                return publicShuttle;
            }).toList());
            return dto;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> jobIdsWithShuttle(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(transportPlanDao.findJobIdsWithMethod(jobIds, TransportMethod.SHUTTLE));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AssignmentTransportDto> viewsForAssignments(List<JobAssignment> assignments, UUID viewerId) {
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Set<UUID> jobIds = assignments.stream().map(assignment -> assignment.getJob().getId())
                .collect(Collectors.toSet());
        List<TransportPlan> plans = transportPlanDao.findAllByJobIdIn(jobIds);
        if (plans.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TransportPlan> byJob = plans.stream()
                .collect(Collectors.toMap(plan -> plan.getJob().getId(), plan -> plan));
        Loaded loaded = load(plans);

        List<UUID> assignmentIds = assignments.stream().map(JobAssignment::getId).toList();
        Map<UUID, List<SeatAssignment>> seats = seatAssignmentDao.findAllByJobAssignmentIdIn(assignmentIds).stream()
                .collect(Collectors.groupingBy(seat -> seat.getJobAssignment().getId()));

        Map<UUID, AssignmentTransportDto> views = new HashMap<>();
        for (JobAssignment assignment : assignments) {
            TransportPlan plan = byJob.get(assignment.getJob().getId());
            if (plan == null) {
                continue;
            }
            views.put(assignment.getId(), assignmentView(plan, assignment,
                    seats.getOrDefault(assignment.getId(), List.of()), viewerId, loaded));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingQrDto issueShuttleQr(UUID shuttleId, boolean staticQr) {
        return shuttleQr(requireShuttleEditor(shuttleId), staticQr);
    }

    @Override
    @Transactional
    public BoardingQrDto rotateShuttleQr(UUID shuttleId) {
        Shuttle shuttle = requireShuttleEditor(shuttleId);
        shuttle.setQrSecret(newSecret());
        shuttleDao.save(shuttle);
        log.info("QR secret of shuttle {} rotated by user {}; every earlier shuttle and worker token is void",
                shuttleId, securityService.getAuthenticatedUserId());
        return shuttleQr(shuttle, false);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingQrDto issueWorkerQr(UUID assignmentId, TransportDirection direction) {
        JobAssignment assignment = requireParty(assignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();
        if (!assignment.getWorker().getId().equals(actorId)) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                    "only the worker may issue a boarding QR");
        }
        if (!SEAT_STATUSES.contains(assignment.getStatus())) {
            throw new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
        TransportDirection wanted = direction == null ? TransportDirection.OUTBOUND : direction;
        SeatAssignment seat = seatAssignmentDao.findByJobAssignmentIdAndDirection(assignmentId, wanted)
                .orElseThrow(() -> new ConflictException(TransportMessages.SEAT_NOT_SELECTED));
        Instant now = clock.instant();
        Duration ttl = boardingProperties.getTokenTtl();
        String token = BoardingTokens.mintWorker(seat.getShuttle().getQrSecret(), seat.getId(), now, ttl);
        return qrDto(token, false, now, ttl, BoardingTokens.workerLink(seat.getId(), token));
    }

    @Override
    @Transactional
    public BoardingEventDto boardOnShuttle(UUID shuttleId, ShuttleBoardingDto request) {
        UUID actorId = securityService.getAuthenticatedUserId();
        SeatAssignment seat = requireWorkerSeatOnShuttle(shuttleId, actorId);
        BoardingTokens.Verified verified;
        try {
            verified = BoardingTokens.verifyShuttle(seat.getShuttle().getQrSecret(), shuttleId, request.getToken(),
                    clock.instant(), boardingProperties.getTokenTtl());
        } catch (BoardingTokens.InvalidToken exception) {
            throw Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                    "boarding token is not valid for this shuttle");
        } catch (BoardingTokens.ExpiredToken exception) {
            throw new ValidationException(TransportMessages.TOKEN_EXPIRED);
        }
        return recordBoarding(seat, new Mark(BoardingKind.BOARDED, BoardingSource.WORKER_SCANNED_SHUTTLE_QR,
                request.getCapturedAt(), request.getLatitude(), request.getLongitude(), request.getClientEventId(),
                verified.staticQr()), actorId);
    }

    @Override
    @Transactional
    public BoardingEventDto markSeatBoarding(UUID seatAssignmentId, SeatBoardingDto request) {
        SeatAssignment seat = requireSeatEditor(seatAssignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();
        if (!SEAT_STATUSES.contains(seat.getJobAssignment().getStatus())) {
            throw new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
        boolean scanned = request.getToken() != null && !request.getToken().isBlank();
        if (scanned) {
            try {
                BoardingTokens.verifyWorker(seat.getShuttle().getQrSecret(), seat.getId(), request.getToken(),
                        clock.instant(), boardingProperties.getTokenTtl());
            } catch (BoardingTokens.InvalidToken exception) {
                throw Concealment.notFound(TransportMessages.SEAT_ASSIGNMENT_NOT_FOUND, actorId, seatAssignmentId,
                        "worker boarding token is not valid for this seat");
            } catch (BoardingTokens.ExpiredToken exception) {
                throw new ValidationException(TransportMessages.TOKEN_EXPIRED);
            }
        }
        BoardingKind kind = scanned || request.getKind() == null ? BoardingKind.BOARDED : request.getKind();
        BoardingSource source = scanned ? BoardingSource.EMPLOYER_SCANNED_WORKER_QR : BoardingSource.EMPLOYER_MARKED;
        return recordBoarding(seat, new Mark(kind, source, request.getCapturedAt(), request.getLatitude(),
                request.getLongitude(), request.getClientEventId(), false), actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShuttleBoardingRowDto> listBoardings(UUID shuttleId) {
        requireShuttleEditor(shuttleId);
        List<SeatAssignment> seats = seatAssignmentDao.findBoardingRowsByShuttleId(shuttleId, BOARDING_LIST_STATUSES);
        if (seats.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<BoardingEvent>> eventsBySeat = boardingEventDao
                .findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc(seats.stream().map(SeatAssignment::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(event -> event.getSeatAssignment().getId()));
        Map<UUID, User> workers = workersOf(seats);
        List<ShuttleBoardingRowDto> rows = new ArrayList<>();
        for (SeatAssignment seat : seats) {
            LocalDate day = boardingDayOf(seat);
            List<BoardingEvent> sameDay = eventsBySeat.getOrDefault(seat.getId(), List.of()).stream()
                    .filter(event -> day.equals(event.getBoardingDay()))
                    .toList();
            Optional<BoardingEvent> boarded = firstBoarded(sameDay);
            BoardingEvent decisive = boarded.orElseGet(() -> sameDay.stream()
                    .filter(event -> event.getKind() == BoardingKind.NO_SHOW)
                    .reduce((earlier, later) -> later)
                    .orElse(null));
            UUID workerId = seat.getJobAssignment().getWorker().getId();
            User worker = workers.get(workerId);
            rows.add(transportMapper.toShuttleBoardingRowDto(seat, workerId,
                    worker == null ? null : worker.getFirstName(),
                    worker == null ? null : worker.getLastName(),
                    boarded.isPresent(),
                    decisive == null ? null : transportMapper.toBoardingEventDto(decisive)));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTimelineEventDto> timelineForStaff(UUID assignmentId) {
        List<AdminTimelineEventDto> events = new ArrayList<>();
        for (SeatAssignment seat : seatAssignmentDao.findAllByJobAssignmentIdIn(List.of(assignmentId))) {
            events.add(transportMapper.toSeatTimelineEvent(seat));
        }
        for (BoardingEvent event : boardingEventDao.findAllByJobAssignmentIdOrderByOccurredAtAscIdAsc(assignmentId)) {
            events.add(transportMapper.toBoardingTimelineEvent(event));
        }
        events.sort(Comparator.comparing(AdminTimelineEventDto::at)
                .thenComparing(AdminTimelineEventDto::type)
                .thenComparing(event -> event.id().toString()));
        return events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findEveningReminderDueIds(Instant now) {
        ZonedDateTime local = now.atZone(reminderProperties.getZoneId());
        if (local.toLocalTime().isBefore(reminderProperties.getEveningAt())) {
            return List.of();
        }
        LocalDate tomorrow = local.toLocalDate().plusDays(1);
        Instant from = tomorrow.atStartOfDay(reminderProperties.getZoneId()).toInstant();
        Instant to = tomorrow.plusDays(1).atStartOfDay(reminderProperties.getZoneId()).toInstant();
        return seatAssignmentDao.findEveningReminderDueIds(from, to, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDepartureReminderDueIds(Instant now) {
        Instant to = now.plus(Duration.ofMinutes(reminderProperties.getDepartureLeadMinutes()));
        return seatAssignmentDao.findDepartureReminderDueIds(now, to, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean sendEveningReminder(UUID seatId, Instant now) {
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElse(null);
        if (seat == null || seat.getEveningReminderSentAt() != null) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_EVENING,
                reminderProperties.getDepartureLeadMinutes());
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_EVENING"));
        seat.setEveningReminderSentAt(now);
        seatAssignmentDao.save(seat);
        return true;
    }

    @Override
    @Transactional
    public boolean sendDepartureReminder(UUID seatId, Instant now) {
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElse(null);
        if (seat == null || seat.getDepartureReminderSentAt() != null) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_DEPARTURE,
                reminderProperties.getDepartureLeadMinutes());
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_DEPARTURE"));
        seat.setDepartureReminderSentAt(now);
        seatAssignmentDao.save(seat);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findBoardingReminderDueIds(Instant now) {
        Instant to = now.plus(Duration.ofMinutes(reminderProperties.getBoardingLeadMinutes()));
        return seatAssignmentDao.findBoardingReminderDueIds(now, to, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean sendBoardingReminder(UUID seatId, Instant now) {
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElse(null);
        if (seat == null || seat.getBoardingReminderSentAt() != null) {
            return false;
        }
        seat.setBoardingReminderSentAt(now);
        seatAssignmentDao.save(seat);
        if (!settledSeatIds(List.of(seat), boardingDayOf(seat)).isEmpty()) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_BOARDING,
                reminderProperties.getBoardingLeadMinutes());
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_BOARDING"));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findNotBoardedDepartureIds(Instant now) {
        Instant to = now.minus(Duration.ofMinutes(reminderProperties.getNotBoardedAfterMinutes()));
        Instant from = to.minus(Duration.ofMinutes(reminderProperties.getNotBoardedLookbackMinutes()));
        return seatAssignmentDao.findNotBoardedDueStopIds(from, to, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean sendNotBoardedNotice(UUID stopId, Instant now) {
        List<SeatAssignment> candidates = seatAssignmentDao.findNotBoardedCandidatesByStopId(stopId, SEAT_STATUSES);
        if (candidates.isEmpty()) {
            return false;
        }
        candidates.forEach(seat -> seat.setNotBoardedNoticeSentAt(now));
        seatAssignmentDao.saveAll(candidates);
        SeatAssignment departure = seatAssignmentDao.findWithDetailsById(candidates.getFirst().getId())
                .orElseThrow(() -> new IllegalStateException("seat " + candidates.getFirst().getId() + " vanished"));
        Set<UUID> settled = settledSeatIds(candidates, boardingDayOf(departure));
        List<SeatAssignment> missing = candidates.stream()
                .filter(seat -> !settled.contains(seat.getId()))
                .toList();
        if (missing.isEmpty()) {
            return false;
        }
        Map<UUID, User> workers = workersOf(missing);
        String names = missing.stream()
                .map(seat -> workers.get(seat.getJobAssignment().getWorker().getId()))
                .filter(Objects::nonNull)
                .map(worker -> DisplayNames.of(worker.getFirstName(), worker.getLastName()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        ShuttleStop stop = departure.getStop();
        Shuttle shuttle = departure.getShuttle();
        Job job = departure.getJobAssignment().getJob();
        String body = messageResolver.format(TransportMessages.NOTICE_NOT_BOARDED, REMINDER_LOCALE,
                reminderProperties.getNotBoardedAfterMinutes(), shuttle.getName(), stopName(stop),
                WALL_TIME.format(stop.getScheduledAt().atZone(reminderProperties.getZoneId())), missing.size(), names);
        Map<String, String> data = new HashMap<>();
        data.put("type", PUSH_TYPE);
        data.put("event", "SHUTTLE_NOTICE_NOT_BOARDED");
        data.put("jobId", job.getId().toString());
        data.put("shuttleId", shuttle.getId().toString());
        data.put("stopId", stop.getId().toString());
        data.put("scheduledAt", stop.getScheduledAt().toString());
        data.put("notBoardedCount", Integer.toString(missing.size()));
        List<User> recipients = jobEditors.editorsOf(job);
        notificationService.preparePushRecipients(recipients.stream().map(User::getId).toList());
        recipients.forEach(recipient -> notifyUser(recipient, job.getTitle(), NotificationTargetType.JOB, job.getId(),
                body, data));
        log.info("Not-boarded notice for stop {} of shuttle {}: {} of {} seats missing, {} recipients",
                stop.getId(), shuttle.getId(), missing.size(), candidates.size(), recipients.size());
        return true;
    }

    private String reminderBody(SeatAssignment seat, String key, int leadMinutes) {
        ShuttleStop stop = seat.getStop();
        String time = WALL_TIME.format(stop.getScheduledAt().atZone(reminderProperties.getZoneId()));
        String body = messageResolver.format(key, REMINDER_LOCALE, time, stopName(stop), leadMinutes);
        String plate = seat.getShuttle().getPlate();
        return plate == null ? body : body + " (" + plate + ")";
    }

    private static String stopName(ShuttleStop stop) {
        if (stop.getLabel() != null) {
            return stop.getLabel();
        }
        return stop.getAddress().getDistrict() != null ? stop.getAddress().getDistrict().getName() : "";
    }

    private Map<String, String> reminderPayload(SeatAssignment seat, String event) {
        Map<String, String> data = new HashMap<>();
        data.put("type", PUSH_TYPE);
        data.put("event", event);
        data.put("jobId", seat.getJobAssignment().getJob().getId().toString());
        data.put("jobAssignmentId", seat.getJobAssignment().getId().toString());
        data.put("shuttleId", seat.getShuttle().getId().toString());
        data.put("stopId", seat.getStop().getId().toString());
        data.put("scheduledAt", seat.getStop().getScheduledAt().toString());
        return data;
    }

    private void notifyPlanUpdated(Job job, Set<UUID> droppedAssignmentIds) {
        List<JobAssignment> assigned = jobAssignmentDao.findAllByJobIdAndStatusIn(job.getId(), NOTIFIED_STATUSES);
        if (assigned.isEmpty()) {
            return;
        }
        notificationService.preparePushRecipients(assigned.stream()
                .map(assignment -> assignment.getWorker().getId())
                .toList());
        String updated = messageResolver.resolve(TransportMessages.PLAN_UPDATED_NOTICE);
        String dropped = messageResolver.resolve(TransportMessages.SEAT_DROPPED_NOTICE);
        for (JobAssignment assignment : assigned) {
            boolean seatDropped = droppedAssignmentIds.contains(assignment.getId());
            Map<String, String> data = Map.of(
                    "type", PUSH_TYPE,
                    "event", seatDropped ? "SEAT_DROPPED" : "PLAN_UPDATED",
                    "jobId", job.getId().toString(),
                    "jobAssignmentId", assignment.getId().toString());
            notifyWorker(assignment, seatDropped ? dropped : updated, data);
        }
    }

    private void notifyWorker(JobAssignment assignment, String body, Map<String, String> data) {
        notifyUser(assignment.getWorker(), assignment.getJob().getTitle(), NotificationTargetType.JOB_ASSIGNMENT,
                assignment.getId(), body, data);
    }

    private void notifyUser(User recipient, String title, NotificationTargetType targetType, UUID targetId,
                            String body, Map<String, String> data) {
        try {
            SendInAppDto inApp = new SendInAppDto();
            inApp.setRecipient(recipient);
            inApp.setCategory(NotificationCategory.TRANSPORT);
            inApp.setSubject(title);
            inApp.setBody(body);
            inApp.setTargetType(targetType);
            inApp.setTargetId(targetId);
            notificationService.sendInApp(inApp);
        } catch (Exception exception) {
            log.warn("Transport in-app notice could not be recorded for user {} on {} {}: {}",
                    recipient.getId(), targetType, targetId, exception.getMessage());
        }
        try {
            SendPushToUserDto push = new SendPushToUserDto();
            push.setRecipient(recipient);
            push.setCategory(NotificationCategory.TRANSPORT);
            push.setTitle(title);
            push.setBody(body);
            push.setDataPayload(data);
            notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);
        } catch (Exception exception) {
            log.warn("Transport push could not be queued for user {} on {} {}: {}",
                    recipient.getId(), targetType, targetId, exception.getMessage());
        }
    }

    private record Mark(BoardingKind kind, BoardingSource source, Instant capturedAt, BigDecimal latitude,
                        BigDecimal longitude, UUID clientEventId, boolean staticQr) {
    }

    private BoardingEventDto recordBoarding(SeatAssignment seat, Mark mark, UUID actorId) {
        seatAssignmentDao.lockById(seat.getId());
        Instant capturedAt = mark.capturedAt() == null ? null : mark.capturedAt().truncatedTo(ChronoUnit.MICROS);
        BigDecimal latitude = normalizeCoordinate(mark.latitude());
        BigDecimal longitude = normalizeCoordinate(mark.longitude());
        if (mark.clientEventId() != null) {
            Optional<BoardingEvent> replay = boardingEventDao.findBySeatAssignmentIdAndClientEventId(seat.getId(),
                    mark.clientEventId());
            if (replay.isPresent()) {
                BoardingEvent stored = replay.get();
                if (stored.getKind() != mark.kind() || !Objects.equals(stored.getCapturedAt(), capturedAt)
                        || !coordinatesEqual(stored.getLatitude(), latitude)
                        || !coordinatesEqual(stored.getLongitude(), longitude)) {
                    log.info("Boarding retry id {} on seat {} reused with a different payload by user {}",
                            mark.clientEventId(), seat.getId(), actorId);
                    throw new ConflictException(TransportMessages.IDEMPOTENCY_CONFLICT);
                }
                return transportMapper.toBoardingEventDto(stored);
            }
        }
        LocalDate boardingDay = boardingDayOf(seat);
        List<BoardingEvent> sameDay = boardingEventDao.findAllBySeatAssignmentIdAndBoardingDayOrderByCreatedAtAscIdAsc(
                seat.getId(), boardingDay);
        Optional<BoardingEvent> boarded = firstBoarded(sameDay);
        if (boarded.isPresent()) {
            if (mark.kind() == BoardingKind.NO_SHOW) {
                throw new ConflictException(TransportMessages.ALREADY_BOARDED);
            }
            log.info("Seat {} already boarded on {}; {} by user {} answered with event {}",
                    seat.getId(), boardingDay, mark.source(), actorId, boarded.get().getId());
            return transportMapper.toBoardingEventDto(boarded.get());
        }
        if (mark.kind() == BoardingKind.NO_SHOW && !sameDay.isEmpty()) {
            return transportMapper.toBoardingEventDto(sameDay.getLast());
        }

        Instant now = clock.instant();
        TimesheetEventPolicy.Outcome outcome = timesheetEventPolicy.reconcile(capturedAt, now, null, null, null);
        EnumSet<TimesheetPlausibilityFlag> flags = EnumSet.noneOf(TimesheetPlausibilityFlag.class);
        flags.addAll(outcome.flags());
        if (mark.kind() == BoardingKind.BOARDED) {
            flags.addAll(boardingFlags(seat, outcome.effectiveAt(), latitude, longitude, mark.staticQr()));
        }
        BoardingEvent event = new BoardingEvent();
        event.setSeatAssignment(seat);
        event.setJobAssignment(seat.getJobAssignment());
        event.setShuttle(seat.getShuttle());
        event.setKind(mark.kind());
        event.setSource(mark.source());
        event.setOccurredAt(outcome.effectiveAt());
        event.setCapturedAt(capturedAt);
        event.setBoardingDay(boardingDay);
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        event.setFlags(flags);
        event.setClientEventId(mark.clientEventId());
        event.setRecordedBy(userService.getReference(actorId));
        BoardingEvent saved = boardingEventDao.save(event);
        log.info("Boarding {} via {} recorded for seat {} on {} by user {} with flags {}",
                mark.kind(), mark.source(), seat.getId(), boardingDay, actorId, flags);
        return transportMapper.toBoardingEventDto(saved);
    }

    private EnumSet<TimesheetPlausibilityFlag> boardingFlags(SeatAssignment seat, Instant occurredAt,
                                                             BigDecimal latitude, BigDecimal longitude,
                                                             boolean staticQr) {
        EnumSet<TimesheetPlausibilityFlag> flags = EnumSet.noneOf(TimesheetPlausibilityFlag.class);
        ShuttleStop stop = seat.getStop();
        if (Duration.between(stop.getScheduledAt(), occurredAt).abs()
                .compareTo(boardingProperties.getScheduleTolerance()) > 0) {
            flags.add(TimesheetPlausibilityFlag.OFF_SCHEDULE);
        }
        Address address = stop.getAddress();
        if (latitude != null && longitude != null && address != null
                && address.getLatitude() != null && address.getLongitude() != null
                && GeoDistance.metersBetween(latitude, longitude, address.getLatitude(), address.getLongitude())
                > boardingProperties.getFarFromStopMeters()) {
            flags.add(TimesheetPlausibilityFlag.FAR_FROM_STOP);
        }
        if (staticQr) {
            flags.add(TimesheetPlausibilityFlag.STATIC_QR);
        }
        return flags;
    }

    private Set<UUID> settledSeatIds(List<SeatAssignment> seats, LocalDate boardingDay) {
        return boardingEventDao.findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc(
                        seats.stream().map(SeatAssignment::getId).toList()).stream()
                .filter(event -> boardingDay.equals(event.getBoardingDay()))
                .filter(event -> SETTLED_KINDS.contains(event.getKind()))
                .map(event -> event.getSeatAssignment().getId())
                .collect(Collectors.toSet());
    }

    private static Optional<BoardingEvent> firstBoarded(List<BoardingEvent> events) {
        return events.stream().filter(event -> event.getKind() == BoardingKind.BOARDED).findFirst();
    }

    private Map<UUID, User> workersOf(List<SeatAssignment> seats) {
        List<UUID> workerIds = seats.stream()
                .map(seat -> seat.getJobAssignment().getWorker().getId())
                .distinct()
                .toList();
        return userService.getAllByIdIn(workerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private BoardingQrDto shuttleQr(Shuttle shuttle, boolean staticQr) {
        Instant now = clock.instant();
        Duration ttl = boardingProperties.getTokenTtl();
        String token = BoardingTokens.mintShuttle(shuttle.getQrSecret(), shuttle.getId(), now, ttl, staticQr);
        return qrDto(token, staticQr, now, ttl, BoardingTokens.shuttleLink(shuttle.getId(), token));
    }

    private static BoardingQrDto qrDto(String token, boolean staticQr, Instant now, Duration ttl, String uri) {
        return new BoardingQrDto(token, staticQr,
                staticQr ? null : BoardingTokens.acceptedUntil(now, ttl),
                staticQr ? null : BoardingTokens.refreshAt(now, ttl),
                uri);
    }

    private static boolean coordinatesEqual(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private static BigDecimal normalizeCoordinate(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private LocalDate boardingDayOf(SeatAssignment seat) {
        return seat.getStop().getScheduledAt().atZone(reminderProperties.getZoneId()).toLocalDate();
    }

    private TransportPlan requirePlan(UUID jobId) {
        return transportPlanDao.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(TransportMessages.PLAN_NOT_FOUND));
    }

    private static Shuttle requireShuttle(TransportPlan plan, UUID shuttleId, UUID actorId) {
        return plan.getShuttles().stream()
                .filter(candidate -> candidate.getId().equals(shuttleId))
                .findFirst()
                .orElseThrow(() -> Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                        "shuttle is not part of the assignment's job plan"));
    }

    private static ShuttleStop requireStop(Shuttle shuttle, UUID stopId, UUID actorId) {
        return shuttle.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> Concealment.notFound(TransportMessages.STOP_NOT_FOUND, actorId, stopId,
                        "stop is not part of the selected shuttle"));
    }

    private Job requireEditor(UUID jobId) {
        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));
        UUID actorId = securityService.getAuthenticatedUserId();
        if (!jobEditors.isEditor(job, actorId)) {
            throw Concealment.notFound(JobMessages.JOB_NOT_FOUND, actorId, jobId, "not the employer of the job");
        }
        return job;
    }

    private JobAssignment requireParty(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentDao.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
        UUID actorId = securityService.getAuthenticatedUserId();
        if (!participants.isParticipant(assignment, actorId)) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                    "not a party of the assignment");
        }
        return assignment;
    }

    private Shuttle requireShuttleEditor(UUID shuttleId) {
        UUID actorId = securityService.getAuthenticatedUserId();
        Shuttle shuttle = shuttleDao.findWithPlanJobById(shuttleId).orElse(null);
        if (shuttle == null || !jobEditors.isEditor(shuttle.getPlan().getJob(), actorId)) {
            throw Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                    "shuttle is missing or caller is not the employer");
        }
        return shuttle;
    }

    private SeatAssignment requireSeatEditor(UUID seatAssignmentId) {
        UUID actorId = securityService.getAuthenticatedUserId();
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatAssignmentId).orElse(null);
        if (seat == null || !jobEditors.isEditor(seat.getJobAssignment().getJob(), actorId)) {
            throw Concealment.notFound(TransportMessages.SEAT_ASSIGNMENT_NOT_FOUND, actorId, seatAssignmentId,
                    "seat assignment is missing or caller is not the employer");
        }
        return seat;
    }

    private SeatAssignment requireWorkerSeatOnShuttle(UUID shuttleId, UUID workerId) {
        List<SeatAssignment> seats = seatAssignmentDao.findAllByShuttleIdAndJobAssignment_Worker_Id(
                shuttleId, workerId);
        if (seats.isEmpty()) {
            throw Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, workerId, shuttleId,
                    "worker has no seat on this shuttle");
        }
        SeatAssignment active = seats.stream()
                .filter(seat -> SEAT_STATUSES.contains(seat.getJobAssignment().getStatus()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE));
        return seatAssignmentDao.findWithDetailsById(active.getId())
                .orElseThrow(() -> Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, workerId, shuttleId,
                        "worker seat could not be loaded"));
    }

    private TransportPlanDto planDto(TransportPlan plan) {
        TransportPlanDto dto = transportMapper.toTransportPlanDto(plan);
        dto.setShuttles(shuttleDtos(plan.getShuttles(), load(List.of(plan)), View.EMPLOYER));
        return dto;
    }

    private AssignmentTransportDto assignmentView(TransportPlan plan, JobAssignment assignment,
                                                  List<SeatAssignment> seats, UUID viewerId, Loaded loaded) {
        boolean workerView = assignment.getWorker() != null && assignment.getWorker().getId().equals(viewerId);
        View view;
        if (!workerView) {
            view = View.EMPLOYER;
        } else if (VIEW_STATUSES.contains(assignment.getStatus())) {
            view = new View(seats.stream().map(seat -> seat.getShuttle().getId()).collect(Collectors.toSet()), true);
        } else {
            view = new View(Set.of(), false);
        }
        AssignmentTransportDto dto = transportMapper.toAssignmentTransportDto(plan);
        dto.setShuttles(shuttleDtos(plan.getShuttles(), loaded, view));
        dto.setSeats(seats.stream().map(transportMapper::toSeatAssignmentDto).toList());
        return dto;
    }

    private record View(Set<UUID> revealedShuttleIds, boolean fullAddresses) {

        private static final View EMPLOYER = new View(null, true);

        boolean reveals(UUID shuttleId) {
            return revealedShuttleIds == null || revealedShuttleIds.contains(shuttleId);
        }
    }

    private List<ShuttleDto> shuttleDtos(List<Shuttle> shuttles, Loaded loaded, View view) {
        List<ShuttleDto> result = new ArrayList<>();
        for (Shuttle shuttle : shuttles) {
            ShuttleDto dto = transportMapper.toShuttleDto(shuttle);
            dto.setSeatsTaken(loaded.seatsByShuttle().getOrDefault(shuttle.getId(), 0L));
            dto.setStops(loaded.stopsByShuttle().getOrDefault(shuttle.getId(), List.of()).stream()
                    .map(stop -> {
                        ShuttleStopDto stopDto = transportMapper.toShuttleStopDto(stop);
                        stopDto.setSeatsTaken(loaded.seatsByStop().getOrDefault(stop.getId(), 0L));
                        if (!view.fullAddresses()) {
                            addressVisibility.approximate(stopDto.getAddress());
                        }
                        return stopDto;
                    })
                    .toList());
            if (!view.reveals(shuttle.getId())) {
                dto.setPlate(null);
                dto.setDriverName(null);
                dto.setDriverPhone(null);
            }
            result.add(dto);
        }
        return result;
    }

    private Loaded load(List<TransportPlan> plans) {
        List<Shuttle> shuttles = plans.stream().flatMap(plan -> plan.getShuttles().stream()).toList();
        if (shuttles.isEmpty()) {
            return new Loaded(Map.of(), Map.of(), Map.of());
        }
        List<UUID> shuttleIds = shuttles.stream().map(Shuttle::getId).toList();
        Map<UUID, List<ShuttleStop>> stops = stopsByShuttle(shuttles);
        Map<UUID, Long> seatsByShuttle = seatAssignmentDao.countByShuttleIds(shuttleIds).stream()
                .collect(Collectors.toMap(SeatAssignmentDao.ShuttleSeatCount::getShuttleId,
                        SeatAssignmentDao.ShuttleSeatCount::getTotal));
        List<UUID> stopIds = stops.values().stream().flatMap(List::stream).map(ShuttleStop::getId).toList();
        Map<UUID, Long> seatsByStop = stopIds.isEmpty() ? Map.of()
                : seatAssignmentDao.countByStopIds(stopIds).stream()
                .collect(Collectors.toMap(SeatAssignmentDao.StopSeatCount::getStopId,
                        SeatAssignmentDao.StopSeatCount::getTotal));
        return new Loaded(stops, seatsByShuttle, seatsByStop);
    }

    private Map<UUID, List<ShuttleStop>> stopsByShuttle(List<Shuttle> shuttles) {
        if (shuttles.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = shuttles.stream().map(Shuttle::getId).toList();
        return shuttleStopDao.findAllByShuttleIdInOrderBySortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(stop -> stop.getShuttle().getId()));
    }

    private record Loaded(Map<UUID, List<ShuttleStop>> stopsByShuttle, Map<UUID, Long> seatsByShuttle,
                          Map<UUID, Long> seatsByStop) {
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
