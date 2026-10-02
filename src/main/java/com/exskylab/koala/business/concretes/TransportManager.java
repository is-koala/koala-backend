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
import com.exskylab.koala.core.dtos.transport.response.SeatAssignmentDto;
import com.exskylab.koala.core.dtos.transport.response.SeatBoardingStatusDto;
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
import com.exskylab.koala.core.properties.AuthLinkProperties;
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
import com.exskylab.koala.entities.BoardingRecorderRole;
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

    private static final Set<AssignmentStatus> BOARDABLE_STATUSES = EnumSet.of(AssignmentStatus.CONFIRMED,
            AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED);

    private static final Set<AssignmentStatus> ALL_STATUSES = EnumSet.allOf(AssignmentStatus.class);

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
    private final AuthLinkProperties authLinkProperties;
    private final JobAddressVisibility addressVisibility = new JobAddressVisibility();

    public TransportManager(TransportPlanDao transportPlanDao, ShuttleDao shuttleDao, ShuttleStopDao shuttleStopDao,
                            SeatAssignmentDao seatAssignmentDao, JobDao jobDao, JobAssignmentDao jobAssignmentDao,
                            SecurityService securityService, JobEditors jobEditors,
                            AddressService addressService, AssignmentParticipants participants,
                            NotificationService notificationService, MessageResolver messageResolver,
                            TransportMapper transportMapper, TransportReminderProperties reminderProperties,
                            BoardingEventDao boardingEventDao, UserService userService, Clock clock,
                            TimesheetEventPolicy timesheetEventPolicy, TransportBoardingProperties boardingProperties,
                            AuthLinkProperties authLinkProperties) {
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
        this.authLinkProperties = authLinkProperties;
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
        Loaded loaded = load(List.of(plan));
        return assignmentView(plan, assignment, seats, actorId, loaded, boardingStatusOf(seats, loaded));
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
        Loaded loaded = load(List.of(plan));
        return assignmentView(plan, assignment, seats, actorId, loaded, boardingStatusOf(seats, loaded));
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
        List<SeatAssignment> allSeats = seatAssignmentDao.findAllByJobAssignmentIdIn(assignmentIds);
        Map<UUID, SeatBoardingStatusDto> boarding = boardingStatusOf(allSeats, loaded);
        Map<UUID, List<SeatAssignment>> seats = allSeats.stream()
                .collect(Collectors.groupingBy(seat -> seat.getJobAssignment().getId()));

        Map<UUID, AssignmentTransportDto> views = new HashMap<>();
        for (JobAssignment assignment : assignments) {
            TransportPlan plan = byJob.get(assignment.getJob().getId());
            if (plan == null) {
                continue;
            }
            views.put(assignment.getId(), assignmentView(plan, assignment,
                    seats.getOrDefault(assignment.getId(), List.of()), viewerId, loaded, boarding));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingQrDto issueShuttleQr(UUID shuttleId, boolean staticQr) {
        Shuttle shuttle = requireShuttleEditor(shuttleId);
        return shuttleQr(shuttle.getId(), shuttle.getQrSecret(), staticQr);
    }

    @Override
    @Transactional
    public BoardingQrDto rotateShuttleQr(UUID shuttleId) {
        Shuttle shuttle = requireShuttleEditor(shuttleId);
        UUID actorId = securityService.getAuthenticatedUserId();
        String secret = newSecret();
        if (shuttleDao.rotateQrSecret(shuttle.getId(), secret) != 1) {
            throw concealedShuttle(actorId, shuttleId, "shuttle vanished before its QR secret was rotated");
        }
        log.info("QR secret of shuttle {} rotated by user {}; every earlier shuttle and worker token is void",
                shuttleId, actorId);
        return shuttleQr(shuttle.getId(), secret, false);
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
        if (!BOARDABLE_STATUSES.contains(assignment.getStatus())) {
            throw new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
        TransportDirection wanted = direction == null ? TransportDirection.OUTBOUND : direction;
        SeatAssignment seat = seatAssignmentDao.findByJobAssignmentIdAndDirection(assignmentId, wanted)
                .orElseThrow(() -> new ConflictException(TransportMessages.SEAT_NOT_SELECTED));
        Instant now = clock.instant();
        Duration ttl = boardingProperties.getTokenTtl();
        return qrDto(BoardingTokens.mintWorker(seat.getShuttle().getQrSecret(), seat.getId(), now, ttl), false, now, ttl);
    }

    @Override
    @Transactional
    public BoardingEventDto boardOnShuttle(UUID shuttleId, ShuttleBoardingDto request) {
        UUID actorId = securityService.getAuthenticatedUserId();
        Shuttle shuttle = shuttleDao.findWithPlanJobById(shuttleId)
                .orElseThrow(() -> concealedShuttle(actorId, shuttleId, "shuttle is missing"));
        UUID jobId = shuttle.getPlan().getJob().getId();
        List<SeatAssignmentDao.WorkerSeat> seats = seatAssignmentDao.findWorkerSeatsOnJob(jobId, actorId);
        Mark mark = new Mark(BoardingKind.BOARDED, BoardingSource.WORKER_SCANNED_SHUTTLE_QR, request.getCapturedAt(),
                request.getLatitude(), request.getLongitude(), request.getClientEventId());
        Optional<SeatAssignmentDao.WorkerSeat> own = seats.stream()
                .filter(seat -> seat.getShuttleId().equals(shuttleId) && BOARDABLE_STATUSES.contains(seat.getStatus()))
                .findFirst();
        Supplier<ResourceNotFoundException> concealed =
                () -> concealedShuttle(actorId, shuttleId, "boarding token or seat does not match this shuttle");
        if (own.isEmpty()) {
            throw refusalWithoutSeat(shuttle, seats, request.getToken(), mark.capturedAt(), actorId, concealed);
        }
        return recordBoarding(own.get().getSeatId(), mark, actorId,
                TransportManager::requireBoardable,
                (seat, now) -> checkToken(BoardingTokens.verifyShuttle(seat.getShuttle().getQrSecret(), shuttleId,
                        request.getToken(), now, boardingProperties.getTokenTtl()), mark.capturedAt(), now, concealed),
                concealed);
    }

    @Override
    @Transactional
    public BoardingEventDto markSeatBoarding(UUID seatAssignmentId, SeatBoardingDto request) {
        UUID actorId = securityService.getAuthenticatedUserId();
        boolean scanned = request.getToken() != null && !request.getToken().isBlank();
        BoardingKind kind = scanned || request.getKind() == null ? BoardingKind.BOARDED : request.getKind();
        Mark mark = new Mark(kind, scanned ? BoardingSource.EMPLOYER_SCANNED_WORKER_QR : BoardingSource.EMPLOYER_MARKED,
                request.getCapturedAt(), request.getLatitude(), request.getLongitude(), request.getClientEventId());
        Supplier<ResourceNotFoundException> concealed =
                () -> concealedSeat(actorId, seatAssignmentId, "seat is missing, caller is not the employer or token mismatch");
        return recordBoarding(seatAssignmentId, mark, actorId,
                seat -> {
                    if (!jobEditors.isEditor(seat.getJobAssignment().getJob(), actorId)) {
                        throw concealed.get();
                    }
                    requireBoardable(seat);
                },
                (seat, now) -> scanned
                        ? checkToken(BoardingTokens.verifyWorker(seat.getShuttle().getQrSecret(), seat.getId(),
                        request.getToken(), now, boardingProperties.getTokenTtl()), mark.capturedAt(), now, concealed)
                        : TokenCheck.NONE,
                concealed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShuttleBoardingRowDto> listBoardings(UUID shuttleId) {
        requireShuttleEditor(shuttleId);
        List<SeatAssignment> seats = seatAssignmentDao.findBoardingRowsByShuttleId(shuttleId, BOARDABLE_STATUSES);
        if (seats.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<BoardingEvent>> eventsBySeat = eventsBySeat(seats);
        Map<UUID, User> workers = workersOf(seats);
        List<ShuttleBoardingRowDto> rows = new ArrayList<>();
        for (SeatAssignment seat : seats) {
            List<BoardingEvent> departure = sameDeparture(eventsBySeat.getOrDefault(seat.getId(), List.of()),
                    shuttleId, boardingDayOf(seat.getStop()));
            BoardingEvent decisive = decisiveEvent(departure);
            UUID workerId = seat.getJobAssignment().getWorker().getId();
            User worker = workers.get(workerId);
            rows.add(transportMapper.toShuttleBoardingRowDto(seat, workerId,
                    worker == null ? null : worker.getFirstName(),
                    worker == null ? null : worker.getLastName(),
                    decisive != null && decisive.getKind() == BoardingKind.BOARDED,
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
            BoardingRecorderRole role = event.getSource() == BoardingSource.WORKER_SCANNED_SHUTTLE_QR
                    ? BoardingRecorderRole.WORKER : BoardingRecorderRole.EMPLOYER;
            events.add(transportMapper.toBoardingTimelineEvent(event, role));
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
        if (seat == null || seatAssignmentDao.claimEveningReminder(seatId, now) == 0) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_EVENING,
                reminderProperties.getDepartureLeadMinutes());
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_EVENING"));
        return true;
    }

    @Override
    @Transactional
    public boolean sendDepartureReminder(UUID seatId, Instant now) {
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElse(null);
        if (seat == null || seatAssignmentDao.claimDepartureReminder(seatId, now) == 0) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_DEPARTURE,
                reminderProperties.getDepartureLeadMinutes());
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_DEPARTURE"));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findBoardingReminderDueIds(Instant now) {
        if (!boardingProperties.isNoticesEnabled()) {
            return List.of();
        }
        Instant to = now.plus(Duration.ofMinutes(reminderProperties.getBoardingLeadMinutes()));
        return seatAssignmentDao.findBoardingReminderDueIds(now, to, TransportDirection.OUTBOUND, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean sendBoardingReminder(UUID seatId, Instant now) {
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElse(null);
        if (seat == null || seat.getDirection() != TransportDirection.OUTBOUND
                || seatAssignmentDao.claimBoardingReminder(seatId, now) == 0) {
            return false;
        }
        if (!settledSeatIds(List.of(seat.getId()), seat.getShuttle().getId(), boardingDayOf(seat.getStop())).isEmpty()) {
            return false;
        }
        String body = reminderBody(seat, TransportMessages.REMINDER_BOARDING,
                reminderProperties.getBoardingLeadMinutes());
        Map<String, String> data = reminderPayload(seat, "SHUTTLE_REMINDER_BOARDING");
        data.put("direction", seat.getDirection().name());
        data.put("seatAssignmentId", seat.getId().toString());
        notifyWorker(seat.getJobAssignment(), body, data);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findNotBoardedDepartureIds(Instant now) {
        if (!boardingProperties.isNoticesEnabled()) {
            return List.of();
        }
        Instant to = now.minus(Duration.ofMinutes(reminderProperties.getNotBoardedAfterMinutes()));
        Instant from = to.minus(Duration.ofMinutes(reminderProperties.getNotBoardedLookbackMinutes()));
        return shuttleStopDao.findNotBoardedDueStopIds(from, to, TransportDirection.OUTBOUND, SEAT_STATUSES,
                PageRequest.of(0, reminderProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean sendNotBoardedNotice(UUID stopId, Instant now) {
        List<SeatAssignment> candidates = seatAssignmentDao.findNotBoardedCandidatesByStopId(stopId, SEAT_STATUSES);
        if (candidates.isEmpty()) {
            return false;
        }
        List<UUID> candidateIds = candidates.stream().map(SeatAssignment::getId).toList();
        if (seatAssignmentDao.claimNotBoardedNotice(candidateIds, now) == 0) {
            return false;
        }
        SeatAssignment departure = seatAssignmentDao.findWithDetailsById(candidateIds.getFirst())
                .orElseThrow(() -> new IllegalStateException("seat " + candidateIds.getFirst() + " vanished"));
        Shuttle shuttle = departure.getShuttle();
        ShuttleStop stop = departure.getStop();
        LocalDate day = boardingDayOf(stop);
        if (departure.getDirection() != TransportDirection.OUTBOUND) {
            return false;
        }
        if (!boardingEventDao.existsByShuttleIdAndBoardingDay(shuttle.getId(), day)) {
            log.info("Not-boarded notice skipped for stop {} of shuttle {}: nobody scanned or marked on {}",
                    stop.getId(), shuttle.getId(), day);
            return false;
        }
        Set<UUID> settled = settledSeatIds(candidateIds, shuttle.getId(), day);
        List<SeatAssignment> missing = candidates.stream()
                .filter(seat -> !settled.contains(seat.getId()))
                .toList();
        if (missing.isEmpty()) {
            return false;
        }
        Map<UUID, User> workers = workersOf(missing);
        String unnamed = messageResolver.resolve(TransportMessages.NOTICE_UNNAMED_WORKER, REMINDER_LOCALE);
        String names = missing.stream()
                .map(seat -> workers.get(seat.getJobAssignment().getWorker().getId()))
                .map(worker -> worker == null ? null : DisplayNames.of(worker.getFirstName(), worker.getLastName()))
                .map(name -> name == null ? unnamed : name)
                .collect(Collectors.joining(", "));
        String time = WALL_TIME.format(stop.getScheduledAt().atZone(reminderProperties.getZoneId()));
        String place = stopName(stop);
        Job job = departure.getJobAssignment().getJob();
        String body = messageResolver.format(TransportMessages.NOTICE_NOT_BOARDED, REMINDER_LOCALE,
                reminderProperties.getNotBoardedAfterMinutes(), shuttle.getName(),
                place.isEmpty() ? time : time + ", " + place, missing.size(), names);
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
                        BigDecimal longitude, UUID clientEventId) {
    }

    private record TokenCheck(Instant windowStart, Instant windowEnd, Set<TimesheetPlausibilityFlag> flags) {

        private static final TokenCheck NONE = new TokenCheck(null, null, Set.of());
    }

    private interface SeatGate {
        void check(SeatAssignment seat);
    }

    private interface TokenGate {
        TokenCheck check(SeatAssignment seat, Instant now);
    }

    private BoardingEventDto recordBoarding(UUID seatId, Mark mark, UUID actorId, SeatGate seatGate,
                                            TokenGate tokenGate, Supplier<ResourceNotFoundException> concealed) {
        seatAssignmentDao.lockById(seatId).orElseThrow(concealed);
        SeatAssignment seat = seatAssignmentDao.findWithDetailsById(seatId).orElseThrow(concealed);
        seatGate.check(seat);
        Instant capturedAt = mark.capturedAt() == null ? null : mark.capturedAt().truncatedTo(ChronoUnit.MICROS);
        BigDecimal latitude = normalizeCoordinate(mark.latitude());
        BigDecimal longitude = normalizeCoordinate(mark.longitude());
        Optional<BoardingEventDto> replay = replayOf(seat, mark, capturedAt, latitude, longitude, actorId);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant now = clock.instant();
        TokenCheck token = tokenGate.check(seat, now);
        TimesheetEventPolicy.Outcome outcome = timesheetEventPolicy.reconcile(capturedAt, now, null, null, null);
        EnumSet<TimesheetPlausibilityFlag> flags = EnumSet.noneOf(TimesheetPlausibilityFlag.class);
        flags.addAll(outcome.flags());
        flags.addAll(token.flags());
        Instant occurredAt = outcome.effectiveAt();
        if (token.windowStart() != null) {
            Duration skew = boardingProperties.getClockSkew();
            if (occurredAt.isBefore(token.windowStart().minus(skew))) {
                occurredAt = token.windowStart();
                flags.add(TimesheetPlausibilityFlag.DEVICE_TIME_IMPLAUSIBLE);
            } else if (occurredAt.isAfter(token.windowEnd().plus(skew))) {
                occurredAt = token.windowEnd();
                flags.add(TimesheetPlausibilityFlag.DEVICE_TIME_IMPLAUSIBLE);
            }
        }
        ShuttleStop stop = seat.getStop();
        Integer distance = distanceToStop(stop, latitude, longitude);
        if (mark.kind() == BoardingKind.BOARDED && seat.getDirection() == TransportDirection.OUTBOUND) {
            if (Duration.between(stop.getScheduledAt(), occurredAt).abs()
                    .compareTo(boardingProperties.getScheduleTolerance()) > 0) {
                flags.add(TimesheetPlausibilityFlag.OFF_SCHEDULE);
            }
            if (distance != null && distance > boardingProperties.getFarFromStopMeters()) {
                flags.add(TimesheetPlausibilityFlag.FAR_FROM_STOP);
            }
        }

        BoardingEvent event = new BoardingEvent();
        event.setSeatAssignment(seat);
        event.setJobAssignment(seat.getJobAssignment());
        event.setShuttle(seat.getShuttle());
        event.setStop(stop);
        event.setStopLabel(stop.getLabel());
        event.setStopScheduledAt(stop.getScheduledAt());
        event.setDistanceToStopMeters(distance);
        event.setKind(mark.kind());
        event.setSource(mark.source());
        event.setOccurredAt(occurredAt);
        event.setCapturedAt(capturedAt);
        event.setBoardingDay(boardingDayOf(stop));
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        event.setFlags(flags);
        event.setClientEventId(mark.clientEventId());
        event.setRecordedBy(userService.getReference(actorId));
        BoardingEvent saved;
        try {
            saved = boardingEventDao.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.named(exception, "uk_boarding_events_client_event_active")) {
                log.warn("Boarding retry id {} on seat {} collided with a concurrent write", mark.clientEventId(),
                        seat.getId());
                throw new ConflictException(TransportMessages.IDEMPOTENCY_CONFLICT);
            }
            if (ConstraintViolations.named(exception, "uk_boarding_events_boarded_seat_day")) {
                log.warn("Boarding of seat {} on shuttle {} collided with a concurrent write", seat.getId(),
                        seat.getShuttle().getId());
                throw new ConflictException(TransportMessages.BOARDING_CONCURRENT);
            }
            throw exception;
        }
        log.info("Boarding {} via {} recorded for seat {} on shuttle {} ({}) by user {} with flags {}",
                mark.kind(), mark.source(), seat.getId(), seat.getShuttle().getId(), event.getBoardingDay(), actorId,
                flags);
        return transportMapper.toBoardingEventDto(saved);
    }

    private Optional<BoardingEventDto> replayOf(SeatAssignment seat, Mark mark, Instant capturedAt, BigDecimal latitude,
                                                BigDecimal longitude, UUID actorId) {
        if (mark.clientEventId() != null) {
            Optional<BoardingEvent> stored = boardingEventDao.findBySeatAssignmentIdAndClientEventId(seat.getId(),
                    mark.clientEventId());
            if (stored.isPresent()) {
                BoardingEvent event = stored.get();
                if (event.getKind() != mark.kind() || !Objects.equals(event.getCapturedAt(), capturedAt)
                        || !coordinatesEqual(event.getLatitude(), latitude)
                        || !coordinatesEqual(event.getLongitude(), longitude)) {
                    log.info("Boarding retry id {} on seat {} reused with a different payload by user {}",
                            mark.clientEventId(), seat.getId(), actorId);
                    throw new ConflictException(TransportMessages.IDEMPOTENCY_CONFLICT);
                }
                return Optional.of(replayed(event));
            }
        }
        List<BoardingEvent> departure = boardingEventDao
                .findAllBySeatAssignmentIdAndShuttleIdAndBoardingDayOrderByCreatedAtAscIdAsc(seat.getId(),
                        seat.getShuttle().getId(), boardingDayOf(seat.getStop()));
        Optional<BoardingEvent> boarded = departure.stream()
                .filter(event -> event.getKind() == BoardingKind.BOARDED)
                .findFirst();
        if (boarded.isPresent()) {
            if (mark.kind() == BoardingKind.NO_SHOW) {
                throw new ConflictException(TransportMessages.ALREADY_BOARDED);
            }
            log.info("Seat {} already boarded on shuttle {}; {} by user {} answered with event {}",
                    seat.getId(), seat.getShuttle().getId(), mark.source(), actorId, boarded.get().getId());
            return Optional.of(replayed(boarded.get()));
        }
        if (mark.kind() == BoardingKind.NO_SHOW && !departure.isEmpty()) {
            return Optional.of(replayed(departure.getLast()));
        }
        return Optional.empty();
    }

    private BoardingEventDto replayed(BoardingEvent event) {
        BoardingEventDto dto = transportMapper.toBoardingEventDto(event);
        dto.setReplayed(true);
        return dto;
    }

    private TokenCheck checkToken(BoardingTokens.Verdict verdict, Instant capturedAt, Instant now,
                                  Supplier<ResourceNotFoundException> concealed) {
        return switch (verdict) {
            case BoardingTokens.Invalid invalid -> throw concealed.get();
            case BoardingTokens.Valid valid when valid.staticQr() ->
                    new TokenCheck(null, null, EnumSet.of(TimesheetPlausibilityFlag.STATIC_QR));
            case BoardingTokens.Valid valid -> new TokenCheck(valid.windowStart(), valid.windowEnd(), Set.of());
            case BoardingTokens.Expired expired -> {
                if (!lateSyncAllowed(expired, capturedAt, now)) {
                    throw new ValidationException(TransportMessages.TOKEN_EXPIRED);
                }
                yield new TokenCheck(expired.windowStart(), expired.windowEnd(),
                        EnumSet.of(TimesheetPlausibilityFlag.LATE_SYNC));
            }
        };
    }

    private boolean lateSyncAllowed(BoardingTokens.Expired expired, Instant capturedAt, Instant now) {
        if (capturedAt == null) {
            return false;
        }
        Duration skew = boardingProperties.getClockSkew();
        return !capturedAt.isBefore(expired.windowStart().minus(skew))
                && !capturedAt.isAfter(expired.windowEnd().plus(skew))
                && !capturedAt.isAfter(now.plus(skew))
                && Duration.between(capturedAt, now).compareTo(boardingProperties.getLateSyncMax()) <= 0;
    }

    private ConflictException refusalWithoutSeat(Shuttle shuttle, List<SeatAssignmentDao.WorkerSeat> seats, String token,
                                                 Instant capturedAt, UUID actorId,
                                                 Supplier<ResourceNotFoundException> concealed) {
        Instant now = clock.instant();
        BoardingTokens.Verdict verdict = BoardingTokens.verifyShuttle(shuttle.getQrSecret(), shuttle.getId(), token, now,
                boardingProperties.getTokenTtl());
        UUID jobId = shuttle.getPlan().getJob().getId();
        if (verdict instanceof BoardingTokens.Invalid
                || !jobAssignmentDao.existsByJobIdAndWorkerIdAndStatusIn(jobId, actorId, ALL_STATUSES)) {
            throw concealed.get();
        }
        checkToken(verdict, capturedAt, now, concealed);
        if (!jobAssignmentDao.existsByJobIdAndWorkerIdAndStatusIn(jobId, actorId, BOARDABLE_STATUSES)) {
            return new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
        boolean seatedElsewhere = seats.stream()
                .anyMatch(seat -> seat.getDirection() == shuttle.getDirection()
                        && BOARDABLE_STATUSES.contains(seat.getStatus()));
        log.info("Boarding refused on shuttle {} for job party {}: {}", shuttle.getId(), actorId,
                seatedElsewhere ? "seat is on another shuttle" : "no seat selected");
        return new ConflictException(seatedElsewhere ? TransportMessages.WRONG_SHUTTLE
                : TransportMessages.SEAT_NOT_SELECTED);
    }

    private static void requireBoardable(SeatAssignment seat) {
        if (!BOARDABLE_STATUSES.contains(seat.getJobAssignment().getStatus())) {
            throw new ConflictException(TransportMessages.ASSIGNMENT_NOT_ACTIVE);
        }
    }

    private static Integer distanceToStop(ShuttleStop stop, BigDecimal latitude, BigDecimal longitude) {
        Address address = stop.getAddress();
        if (latitude == null || longitude == null || address == null || address.getLatitude() == null
                || address.getLongitude() == null) {
            return null;
        }
        return (int) Math.round(GeoDistance.metersBetween(latitude, longitude, address.getLatitude(),
                address.getLongitude()));
    }

    private Map<UUID, List<BoardingEvent>> eventsBySeat(Collection<SeatAssignment> seats) {
        return boardingEventDao.findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc(
                        seats.stream().map(SeatAssignment::getId).toList()).stream()
                .collect(Collectors.groupingBy(event -> event.getSeatAssignment().getId()));
    }

    private static List<BoardingEvent> sameDeparture(List<BoardingEvent> events, UUID shuttleId, LocalDate day) {
        return events.stream()
                .filter(event -> shuttleId.equals(event.getShuttle().getId()) && day.equals(event.getBoardingDay()))
                .toList();
    }

    private static BoardingEvent decisiveEvent(List<BoardingEvent> departure) {
        return departure.stream()
                .filter(event -> event.getKind() == BoardingKind.BOARDED)
                .findFirst()
                .orElseGet(() -> departure.stream()
                        .filter(event -> event.getKind() == BoardingKind.NO_SHOW)
                        .reduce((earlier, later) -> later)
                        .orElse(null));
    }

    private Set<UUID> settledSeatIds(Collection<UUID> seatIds, UUID shuttleId, LocalDate day) {
        return boardingEventDao.findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc(seatIds).stream()
                .filter(event -> shuttleId.equals(event.getShuttle().getId()) && day.equals(event.getBoardingDay()))
                .filter(event -> SETTLED_KINDS.contains(event.getKind()))
                .map(event -> event.getSeatAssignment().getId())
                .collect(Collectors.toSet());
    }

    private Map<UUID, SeatBoardingStatusDto> boardingStatusOf(List<SeatAssignment> seats, Loaded loaded) {
        if (seats.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ShuttleStop> stops = loaded.stopsByShuttle().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(ShuttleStop::getId, Function.identity()));
        Map<UUID, List<BoardingEvent>> events = eventsBySeat(seats);
        Map<UUID, SeatBoardingStatusDto> statuses = new HashMap<>();
        for (SeatAssignment seat : seats) {
            ShuttleStop stop = stops.get(seat.getStop().getId());
            if (stop == null) {
                continue;
            }
            BoardingEvent decisive = decisiveEvent(sameDeparture(events.getOrDefault(seat.getId(), List.of()),
                    seat.getShuttle().getId(), boardingDayOf(stop)));
            if (decisive != null) {
                statuses.put(seat.getId(), new SeatBoardingStatusDto(decisive.getKind(), decisive.getOccurredAt()));
            }
        }
        return statuses;
    }

    private Map<UUID, User> workersOf(List<SeatAssignment> seats) {
        List<UUID> workerIds = seats.stream()
                .map(seat -> seat.getJobAssignment().getWorker().getId())
                .distinct()
                .toList();
        return userService.getAllByIdIn(workerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private BoardingQrDto shuttleQr(UUID shuttleId, String secret, boolean staticQr) {
        Instant now = clock.instant();
        Duration ttl = boardingProperties.getTokenTtl();
        return qrDto(BoardingTokens.mintShuttle(secret, shuttleId, now, ttl, staticQr), staticQr, now, ttl);
    }

    private BoardingQrDto qrDto(String token, boolean staticQr, Instant now, Duration ttl) {
        return new BoardingQrDto(token, staticQr, now,
                staticQr ? null : BoardingTokens.acceptedUntil(now, ttl),
                staticQr ? null : BoardingTokens.refreshAt(now, ttl),
                BoardingTokens.link(authLinkProperties.getAppScheme(), token));
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

    private LocalDate boardingDayOf(ShuttleStop stop) {
        return stop.getScheduledAt().atZone(reminderProperties.getZoneId()).toLocalDate();
    }

    private static ResourceNotFoundException concealedShuttle(UUID actorId, UUID shuttleId, String refusal) {
        return Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId, refusal);
    }

    private static ResourceNotFoundException concealedSeat(UUID actorId, UUID seatAssignmentId, String refusal) {
        return Concealment.notFound(TransportMessages.SEAT_ASSIGNMENT_NOT_FOUND, actorId, seatAssignmentId, refusal);
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

    private TransportPlanDto planDto(TransportPlan plan) {
        TransportPlanDto dto = transportMapper.toTransportPlanDto(plan);
        dto.setShuttles(shuttleDtos(plan.getShuttles(), load(List.of(plan)), View.EMPLOYER));
        return dto;
    }

    private AssignmentTransportDto assignmentView(TransportPlan plan, JobAssignment assignment,
                                                  List<SeatAssignment> seats, UUID viewerId, Loaded loaded,
                                                  Map<UUID, SeatBoardingStatusDto> boarding) {
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
        dto.setSeats(seats.stream().map(seat -> {
            SeatAssignmentDto seatDto = transportMapper.toSeatAssignmentDto(seat);
            seatDto.setBoarding(boarding.get(seat.getId()));
            return seatDto;
        }).toList());
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
