package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleStopWriteDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleWriteDto;
import com.exskylab.koala.core.dtos.transport.request.TransportPlanWriteDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.JobTransportDto;
import com.exskylab.koala.core.dtos.transport.response.PublicShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleStopDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.mappers.TransportMapper;
import com.exskylab.koala.core.properties.TransportReminderProperties;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.SeatAssignmentDao;
import com.exskylab.koala.dataAccess.ShuttleDao;
import com.exskylab.koala.dataAccess.ShuttleStopDao;
import com.exskylab.koala.dataAccess.TransportPlanDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.SeatAssignment;
import com.exskylab.koala.entities.Shuttle;
import com.exskylab.koala.entities.ShuttleStop;
import com.exskylab.koala.entities.TransportMethod;
import com.exskylab.koala.entities.TransportPlan;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransportManager implements TransportService {

    private static final Set<AssignmentStatus> SEAT_STATUSES = EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION,
            AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS);

    private static final Set<AssignmentStatus> VIEW_STATUSES = EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION,
            AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED);

    private static final Set<AssignmentStatus> NOTIFIED_STATUSES = EnumSet.of(AssignmentStatus.CONFIRMED,
            AssignmentStatus.IN_PROGRESS);

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
    private final CompanyContactService companyContactService;
    private final AddressService addressService;
    private final AssignmentParticipants participants;
    private final NotificationService notificationService;
    private final MessageResolver messageResolver;
    private final TransportMapper transportMapper;
    private final TransportReminderProperties reminderProperties;

    public TransportManager(TransportPlanDao transportPlanDao, ShuttleDao shuttleDao, ShuttleStopDao shuttleStopDao,
                            SeatAssignmentDao seatAssignmentDao, JobDao jobDao, JobAssignmentDao jobAssignmentDao,
                            SecurityService securityService, CompanyContactService companyContactService,
                            AddressService addressService, AssignmentParticipants participants,
                            NotificationService notificationService, MessageResolver messageResolver,
                            TransportMapper transportMapper, TransportReminderProperties reminderProperties) {
        this.transportPlanDao = transportPlanDao;
        this.shuttleDao = shuttleDao;
        this.shuttleStopDao = shuttleStopDao;
        this.seatAssignmentDao = seatAssignmentDao;
        this.jobDao = jobDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.addressService = addressService;
        this.participants = participants;
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
        this.transportMapper = transportMapper;
        this.reminderProperties = reminderProperties;
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

        Map<UUID, Shuttle> existing = plan.getShuttles().stream()
                .collect(Collectors.toMap(Shuttle::getId, shuttle -> shuttle));
        List<ShuttleWriteDto> requested = request.getShuttles() == null ? List.of() : request.getShuttles();

        Set<UUID> keptShuttleIds = new HashSet<>();
        Set<UUID> removedStopIds = new HashSet<>();
        Map<UUID, Integer> requestedCapacity = new HashMap<>();
        List<Shuttle> added = new ArrayList<>();
        int order = 0;
        for (ShuttleWriteDto dto : requested) {
            Shuttle shuttle;
            if (dto.getId() == null) {
                shuttle = new Shuttle();
                shuttle.setPlan(plan);
                shuttle.setQrSecret(newSecret());
                added.add(shuttle);
            } else {
                shuttle = requireShuttle(existing, dto.getId(), actorId);
                if (!keptShuttleIds.add(shuttle.getId())) {
                    throw new ValidationException(TransportMessages.SHUTTLE_DUPLICATE);
                }
                requestedCapacity.put(shuttle.getId(), dto.getCapacity());
            }
            shuttle.setDirection(dto.getDirection());
            shuttle.setName(dto.getName().trim());
            shuttle.setPlate(blankToNull(dto.getPlate()));
            shuttle.setCapacity(dto.getCapacity());
            shuttle.setDriverName(blankToNull(dto.getDriverName()));
            shuttle.setDriverPhone(blankToNull(dto.getDriverPhone()));
            shuttle.setSortOrder(order++);
            removedStopIds.addAll(applyStops(shuttle, dto.getStops(), actorId));
        }

        Set<UUID> removedShuttleIds = existing.keySet().stream()
                .filter(id -> !keptShuttleIds.contains(id))
                .collect(Collectors.toSet());
        dropSeats(removedShuttleIds, removedStopIds);

        plan.getShuttles().removeIf(shuttle -> removedShuttleIds.contains(shuttle.getId()));
        plan.getShuttles().addAll(added);
        TransportPlan saved = transportPlanDao.save(plan);
        transportPlanDao.flush();
        rejectCapacityBelowSeats(requestedCapacity);

        log.info("Transport plan {} for job {} written by user {}: {} shuttles, {} removed",
                saved.getId(), jobId, actorId, requested.size(), removedShuttleIds.size());
        notifyPlanUpdated(job);
        return planDto(saved);
    }

    private Set<UUID> applyStops(Shuttle shuttle, List<ShuttleStopWriteDto> requested, UUID actorId) {
        Map<UUID, ShuttleStop> existing = shuttle.getStops().stream()
                .collect(Collectors.toMap(ShuttleStop::getId, stop -> stop));
        Set<UUID> kept = new HashSet<>();
        List<ShuttleStop> added = new ArrayList<>();
        int order = 0;
        for (ShuttleStopWriteDto dto : requested) {
            ShuttleStop stop;
            if (dto.getId() == null) {
                stop = new ShuttleStop();
                stop.setShuttle(shuttle);
                added.add(stop);
            } else {
                stop = requireStop(existing, dto.getId(), actorId);
                if (!kept.add(stop.getId())) {
                    throw new ValidationException(TransportMessages.STOP_DUPLICATE);
                }
            }
            if (dto.getAddress() != null) {
                stop.setAddress(addressService.createAddress(dto.getAddress()));
            }
            stop.setLabel(blankToNull(dto.getLabel()));
            stop.setScheduledAt(dto.getScheduledAt());
            stop.setSortOrder(order++);
        }
        Set<UUID> removed = existing.keySet().stream()
                .filter(id -> !kept.contains(id))
                .collect(Collectors.toSet());
        shuttle.getStops().removeIf(stop -> removed.contains(stop.getId()));
        shuttle.getStops().addAll(added);
        return removed;
    }

    private void dropSeats(Set<UUID> removedShuttleIds, Set<UUID> removedStopIds) {
        Map<UUID, SeatAssignment> doomed = new LinkedHashMap<>();
        if (!removedShuttleIds.isEmpty()) {
            seatAssignmentDao.findAllByShuttleIdIn(removedShuttleIds).forEach(seat -> doomed.put(seat.getId(), seat));
        }
        if (!removedStopIds.isEmpty()) {
            seatAssignmentDao.findAllByStopIdIn(removedStopIds).forEach(seat -> doomed.put(seat.getId(), seat));
        }
        if (doomed.isEmpty()) {
            return;
        }
        seatAssignmentDao.deleteAll(doomed.values());
        seatAssignmentDao.flush();
        log.info("{} seat assignments dropped because their shuttle or stop left the plan", doomed.size());
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
        Shuttle shuttle = requireLockedShuttle(plan, request.getShuttleId(), actorId);
        ShuttleStop stop = requireStop(shuttle, request.getStopId(), actorId);

        SeatAssignment seat = seatAssignmentDao.findByJobAssignmentIdAndDirection(assignmentId, shuttle.getDirection())
                .orElse(null);
        boolean sameShuttle = seat != null && seat.getShuttle().getId().equals(shuttle.getId());
        if (!sameShuttle) {
            long taken = seatAssignmentDao.countByShuttleId(shuttle.getId());
            if (taken >= shuttle.getCapacity()) {
                log.info("Seat refused on shuttle {} for assignment {}: {} of {} seats taken",
                        shuttle.getId(), assignmentId, taken, shuttle.getCapacity());
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
        }
        seatAssignmentDao.save(seat);
        seatAssignmentDao.flush();
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
        String body = reminderBody(seat, TransportMessages.REMINDER_EVENING);
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
        String body = reminderBody(seat, TransportMessages.REMINDER_DEPARTURE);
        notifyWorker(seat.getJobAssignment(), body, reminderPayload(seat, "SHUTTLE_REMINDER_DEPARTURE"));
        seat.setDepartureReminderSentAt(now);
        seatAssignmentDao.save(seat);
        return true;
    }

    private String reminderBody(SeatAssignment seat, String key) {
        ShuttleStop stop = seat.getStop();
        String time = WALL_TIME.format(stop.getScheduledAt().atZone(reminderProperties.getZoneId()));
        String stopName = stop.getLabel() != null ? stop.getLabel()
                : stop.getAddress().getDistrict() != null ? stop.getAddress().getDistrict().getName() : "";
        String body = messageResolver.format(key, REMINDER_LOCALE, time, stopName,
                reminderProperties.getDepartureLeadMinutes());
        String plate = seat.getShuttle().getPlate();
        return plate == null ? body : body + " (" + plate + ")";
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

    private void notifyPlanUpdated(Job job) {
        List<JobAssignment> assigned = jobAssignmentDao.findAllByJobIdAndStatusIn(job.getId(), NOTIFIED_STATUSES);
        if (assigned.isEmpty()) {
            return;
        }
        notificationService.preparePushRecipients(assigned.stream()
                .map(assignment -> assignment.getWorker().getId())
                .toList());
        String body = messageResolver.resolve(TransportMessages.PLAN_UPDATED_NOTICE);
        for (JobAssignment assignment : assigned) {
            Map<String, String> data = Map.of(
                    "type", PUSH_TYPE,
                    "event", "PLAN_UPDATED",
                    "jobId", job.getId().toString(),
                    "jobAssignmentId", assignment.getId().toString());
            notifyWorker(assignment, body, data);
        }
    }

    private void notifyWorker(JobAssignment assignment, String body, Map<String, String> data) {
        User worker = assignment.getWorker();
        String title = assignment.getJob().getTitle();
        try {
            SendInAppDto inApp = new SendInAppDto();
            inApp.setRecipient(worker);
            inApp.setCategory(NotificationCategory.TRANSPORT);
            inApp.setSubject(title);
            inApp.setBody(body);
            inApp.setTargetType(NotificationTargetType.JOB_ASSIGNMENT);
            inApp.setTargetId(assignment.getId());
            notificationService.sendInApp(inApp);
        } catch (Exception exception) {
            log.warn("Transport in-app notice could not be recorded for worker {} on assignment {}: {}",
                    worker.getId(), assignment.getId(), exception.getMessage());
        }
        try {
            SendPushToUserDto push = new SendPushToUserDto();
            push.setRecipient(worker);
            push.setCategory(NotificationCategory.TRANSPORT);
            push.setTitle(title);
            push.setBody(body);
            push.setDataPayload(data);
            notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);
        } catch (Exception exception) {
            log.warn("Transport push could not be queued for worker {} on assignment {}: {}",
                    worker.getId(), assignment.getId(), exception.getMessage());
        }
    }

    private TransportPlan requirePlan(UUID jobId) {
        return transportPlanDao.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(TransportMessages.PLAN_NOT_FOUND));
    }

    private Shuttle requireLockedShuttle(TransportPlan plan, UUID shuttleId, UUID actorId) {
        return shuttleDao.findByIdForUpdate(shuttleId)
                .filter(candidate -> candidate.getPlan().getId().equals(plan.getId()))
                .orElseThrow(() -> Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                        "shuttle is not part of the assignment's job plan"));
    }

    private static Shuttle requireShuttle(Map<UUID, Shuttle> planShuttles, UUID shuttleId, UUID actorId) {
        Shuttle shuttle = planShuttles.get(shuttleId);
        if (shuttle == null) {
            throw Concealment.notFound(TransportMessages.SHUTTLE_NOT_FOUND, actorId, shuttleId,
                    "shuttle is not part of the job's transport plan");
        }
        return shuttle;
    }

    private static ShuttleStop requireStop(Shuttle shuttle, UUID stopId, UUID actorId) {
        return shuttle.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> Concealment.notFound(TransportMessages.STOP_NOT_FOUND, actorId, stopId,
                        "stop is not part of the selected shuttle"));
    }

    private static ShuttleStop requireStop(Map<UUID, ShuttleStop> shuttleStops, UUID stopId, UUID actorId) {
        ShuttleStop stop = shuttleStops.get(stopId);
        if (stop == null) {
            throw Concealment.notFound(TransportMessages.STOP_NOT_FOUND, actorId, stopId,
                    "stop is not part of the shuttle");
        }
        return stop;
    }

    private Job requireEditor(UUID jobId) {
        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));
        UUID actorId = securityService.getAuthenticatedUserId();
        boolean owner = job.getCreator() != null && job.getCreator().getId().equals(actorId);
        boolean contact = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), actorId);
        if (!owner && !contact) {
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

    private TransportPlanDto planDto(TransportPlan plan) {
        TransportPlanDto dto = transportMapper.toTransportPlanDto(plan);
        dto.setShuttles(shuttleDtos(plan.getShuttles(), load(List.of(plan)), null));
        return dto;
    }

    private AssignmentTransportDto assignmentView(TransportPlan plan, JobAssignment assignment,
                                                  List<SeatAssignment> seats, UUID viewerId, Loaded loaded) {
        boolean workerView = assignment.getWorker() != null && assignment.getWorker().getId().equals(viewerId);
        Set<UUID> revealed;
        if (!workerView) {
            revealed = null;
        } else if (VIEW_STATUSES.contains(assignment.getStatus())) {
            revealed = seats.stream().map(seat -> seat.getShuttle().getId()).collect(Collectors.toSet());
        } else {
            revealed = Set.of();
        }
        AssignmentTransportDto dto = transportMapper.toAssignmentTransportDto(plan);
        dto.setShuttles(shuttleDtos(plan.getShuttles(), loaded, revealed));
        dto.setSeats(seats.stream().map(transportMapper::toSeatAssignmentDto).toList());
        return dto;
    }

    private List<ShuttleDto> shuttleDtos(List<Shuttle> shuttles, Loaded loaded, Set<UUID> revealed) {
        List<ShuttleDto> result = new ArrayList<>();
        for (Shuttle shuttle : shuttles) {
            ShuttleDto dto = transportMapper.toShuttleDto(shuttle);
            dto.setSeatsTaken(loaded.seatsByShuttle().getOrDefault(shuttle.getId(), 0L));
            dto.setStops(loaded.stopsByShuttle().getOrDefault(shuttle.getId(), List.of()).stream()
                    .map(stop -> {
                        ShuttleStopDto stopDto = transportMapper.toShuttleStopDto(stop);
                        stopDto.setSeatsTaken(loaded.seatsByStop().getOrDefault(stop.getId(), 0L));
                        return stopDto;
                    })
                    .toList());
            if (revealed != null && !revealed.contains(shuttle.getId())) {
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
