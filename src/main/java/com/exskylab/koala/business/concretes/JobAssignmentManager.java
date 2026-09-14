package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.dataAccess.CancellationDao;
import com.exskylab.koala.dataAccess.ReviewDao;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PricingService;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.AddressFormatter;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.mappers.PricingDtoMapper;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.*;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PartyLink;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.JobAddressVisibility;
import com.exskylab.koala.entities.CancellationParty;
import java.time.Instant;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.properties.AutoCompletionProperties;
import com.exskylab.koala.core.properties.UnpaidExpiryProperties;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.core.events.AssignmentClosedEvent;
import com.exskylab.koala.core.helpers.StaffSearchQuery;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

@Service
@Slf4j
public class JobAssignmentManager implements JobAssignmentService {

    private static final java.util.Set<AssignmentStatus> LIVE_ASSIGNMENT_STATUSES =
            java.util.EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED,
                    AssignmentStatus.IN_PROGRESS);
    private static final Comparator<JobAssignment> OLDEST_FIRST =
            Comparator.comparing(JobAssignment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    static final List<AssignmentStatus> ACTIVE_WORK_STATUSES =
            List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS);

    private static final java.util.Set<AssignmentStatus> SLOT_OCCUPYING_STATUSES =
            java.util.EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED,
                    AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED);

    private static final java.util.Set<String> MY_ASSIGNMENTS_SORTABLE =
            java.util.Set.of("assignmentDateTime", "actualCompletionDateTime", "status");

    private static final java.util.Set<String> ADMIN_LIST_SORTABLE =
            java.util.Set.of("createdAt", "assignmentDateTime", "status", "paymentStatus");

    private static final Sort ADMIN_LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");


    private final JobAssignmentDao jobAssignmentDao;
    private final SecurityService securityService;
    private final JobAssignmentMapper jobAssignmentMapper;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final CompanyContactService companyContactService;
    private final UserService userService;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final PaymentStateMachine paymentStateMachine;
    private final AppProperties appProperties;
    private final BankAccountService bankAccountService;
    private final PageableSanitizer pageableSanitizer;
    private final AssignmentParticipants participants;
    private final BadgeService badgeService;
    private final PayoutService payoutService;
    private final AutoCompletionProperties autoCompletionProperties;
    private final UnpaidExpiryProperties unpaidExpiryProperties;
    private final SettingsService settingsService;
    private final GalleryService galleryService;
    private final ActivityNotificationService activityNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMetrics paymentMetrics;
    private final CancellationDao cancellationDao;
    private final JobService jobService;
    private final ReviewDao reviewDao;
    private final PricingDtoMapper pricingDtoMapper;
    private final JobAddressVisibility addressVisibility = new JobAddressVisibility();


    public JobAssignmentManager(JobAssignmentDao jobAssignmentDao, SecurityService securityService, JobAssignmentMapper jobAssignmentMapper, IyzicoPaymentService iyzicoPaymentService, CompanyContactService companyContactService, UserService userService,
                               PricingService pricingService, PaymentService paymentService,
                               PaymentStateMachine paymentStateMachine, AppProperties appProperties,
                               BankAccountService bankAccountService, PageableSanitizer pageableSanitizer,
                               AssignmentParticipants participants, BadgeService badgeService,
                               PayoutService payoutService,
                               AutoCompletionProperties autoCompletionProperties,
                               UnpaidExpiryProperties unpaidExpiryProperties,
                               SettingsService settingsService, GalleryService galleryService,
                               ActivityNotificationService activityNotificationService,
                               ApplicationEventPublisher eventPublisher, PaymentMetrics paymentMetrics,
                               CancellationDao cancellationDao, JobService jobService, ReviewDao reviewDao,
                               PricingDtoMapper pricingDtoMapper) {
        this.jobAssignmentDao = jobAssignmentDao;
        this.securityService = securityService;
        this.jobAssignmentMapper = jobAssignmentMapper;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.companyContactService = companyContactService;
        this.userService = userService;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.paymentStateMachine = paymentStateMachine;
        this.appProperties = appProperties;
        this.bankAccountService = bankAccountService;
        this.pageableSanitizer = pageableSanitizer;
        this.participants = participants;
        this.badgeService = badgeService;
        this.payoutService = payoutService;
        this.autoCompletionProperties = autoCompletionProperties;
        this.unpaidExpiryProperties = unpaidExpiryProperties;
        this.settingsService = settingsService;
        this.galleryService = galleryService;
        this.activityNotificationService = activityNotificationService;
        this.eventPublisher = eventPublisher;
        this.paymentMetrics = paymentMetrics;
        this.cancellationDao = cancellationDao;
        this.jobService = jobService;
        this.reviewDao = reviewDao;
        this.pricingDtoMapper = pricingDtoMapper;
    }


    @Override
    public JobAssignment save(JobAssignment jobAssignment) {
        log.debug("Creating job assignment from application");
        return jobAssignmentDao.save(jobAssignment);
    }

    @Override
    @Transactional
    public MyAssignmentDto answerToAssignment(UUID assignmentId, AnswerAssignmentDto jobAssignmentsAssignmentIdStatusPatchRequestDto) {
        log.info("Answering to job assignment from");
        var assignment = jobAssignmentDao.findWithDetailsForPaymentById(assignmentId).orElseThrow(() -> {
            log.info("Assignment with id {} not found", assignmentId);
            return new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND);
        });

        log.info("Job assignment with id {} found", assignmentId);

        var authenticatedUserFromContext = securityService.getAuthenticatedUser();

        if (!assignment.getWorker().getId().equals(authenticatedUserFromContext.getId())) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, authenticatedUserFromContext.getId(),
                    assignmentId, "not the worker of the assignment");
        }

        if (assignment.getStatus() != AssignmentStatus.PENDING_CONFIRMATION){
            log.warn("Assignment with ID: {} is already answered.", assignment.getId());
            throw new ConflictException(JobAssignmentMessages.ALREADY_ANSWERED);
        }

        if (Boolean.TRUE.equals(jobAssignmentsAssignmentIdStatusPatchRequestDto.getAccepted())){
            assignment.setStatus(AssignmentStatus.CONFIRMED);
            assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
            assignment.setConfirmedAt(Instant.now());
            log.info("Job assignment with id {} has been accepted", assignmentId);
        }else{
            assignment.setStatus(AssignmentStatus.REJECTED);
            log.info("Job assignment with id {} has been rejected", assignmentId);
        }

        assignment = jobAssignmentDao.save(assignment);

        log.info("Job assignment with id {} has been saved", assignmentId);

        if (assignment.getStatus() == AssignmentStatus.REJECTED) {
            eventPublisher.publishEvent(new AssignmentClosedEvent(
                    assignment.getJob().getId(), assignment.getWorker().getId(), assignment.getId()));
        }

        activityNotificationService.assignmentAnswered(assignment);

        return toMyAssignmentDto(assignment, authenticatedUserFromContext.getId(),
                LIVE_ASSIGNMENT_STATUSES.contains(assignment.getStatus()));

    }


    @Transactional
    @Override
    public PaymentSessionDto initiatePaymentSession(UUID assignmentId, String clientIpAddress) {
        log.info("Initiating job assignment with id {}", assignmentId);
        var assignment = jobAssignmentDao.findById(assignmentId).orElseThrow(() -> {
            log.info("Assignment with id {} not found", assignmentId);
            return new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND);
        });

        log.info("Job assignment with id {} found", assignmentId);


        if (!paymentStateMachine.acceptsPayment(assignment)){
            log.warn("Assignment with ID: {} is not in a state to initiate payment. Status: {}, PaymentStatus: {}", assignment.getId(), assignment.getStatus(), assignment.getPaymentStatus());
            throw new ConflictException(JobAssignmentMessages.ASSIGNMENT_NOT_READY_FOR_PAYMENT);
        }

        var currentUser = securityService.getAuthenticatedUser();
        var worker = assignment.getWorker();
        var job = assignment.getJob();
        var creator = job.getCreator();

        switch (job.getEmployerType()){
            case INDIVIDUAL -> {
               if (!currentUser.getId().equals(creator.getId())) {
                     log.warn("User with id {} is not the creator of the job with id {}", currentUser.getId(), job.getId());
                     throw new ForbiddenException(JobAssignmentMessages.NOT_CREATOR_OF_JOB);
               }

               if (!creator.isIdentityVerified() || creator.getTcIdentityNumber() == null) {
                   log.warn("Payment refused on assignment {}: the individual employer {} is not identity verified.",
                           assignmentId, creator.getId());
                   throw new ValidationException(JobAssignmentMessages.EMPLOYER_IDENTITY_NOT_VERIFIED);
               }
               break;
            }
            case COMPANY -> {
                boolean isContact = companyContactService.isUserAContactOfCompany(job.getCompany().getId(), currentUser.getId());
                if (!isContact) {
                    log.warn("User with id {} is not a contact of the company with id {}", currentUser.getId(), job.getCompany().getId());
                    throw new ForbiddenException(JobAssignmentMessages.USER_IS_NOT_A_CONTACT_OF_COMPANY);
                }
            }
            default -> {
                log.error("Unknown employer type for job with id {}", job.getId());
                throw new IllegalArgumentException("Unknown employer type");
            }
        }

        if (paymentService.hasPendingAttemptForAssignment(assignmentId)) {
            throw new ConflictException(PaymentMessages.ATTEMPT_IN_PROGRESS);
        }

        CommissionCalculator.Breakdown breakdown = pricingService.breakdown(assignment);
        SubMerchantCreateRequestDto merchantDto = null;
        if (worker.getIyzicoSubmerchantKey() == null || worker.getIyzicoSubmerchantKey().isBlank()) {
            merchantDto = createSubMerchantDtoForWorker(worker);
        }

        PaymentSessionRequestDto iyzicoRequest = new PaymentSessionRequestDto();
        iyzicoRequest.setPrice(breakdown.totalCharged());
        iyzicoRequest.setPaidPrice(breakdown.totalCharged());
        iyzicoRequest.setCurrency("TRY");
        iyzicoRequest.setBasketId(assignment.getId().toString());
        iyzicoRequest.setPaymentGroup("PRODUCT");
        iyzicoRequest.setCallbackUrl(appProperties.apiUrl() + "/payments/callback");
        fillBuyerAndAddressInfo(iyzicoRequest, job, clientIpAddress, currentUser);

        Payment payment = paymentService.startAttempt(assignment, breakdown);

        if (merchantDto != null) {
            log.info("Worker {} does not have an iyzico submerchant key. Creating one...", worker.getId());

            try {
                SubMerchantCreateResponseDto response = iyzicoPaymentService.createSubMerchant(merchantDto);

                worker.setIyzicoSubmerchantKey(response.getSubMerchantKey());
                userService.save(worker);
                log.info("Worker {} has been created with iyzico submerchant key.}", worker.getId());
            } catch (RuntimeException exception) {
                paymentService.failInitiation(payment.getId());
                throw exception;
            }
        }

        iyzicoRequest.setConversationId(payment.getConversationId());

        IyzicoBasketItemDto basketItem = new IyzicoBasketItemDto();
        basketItem.setId(job.getId().toString());
        basketItem.setName(job.getTitle());
        basketItem.setPrice(breakdown.totalCharged());
        basketItem.setSubMerchantKey(worker.getIyzicoSubmerchantKey());
        basketItem.setSubMerchantPrice(breakdown.workerPayout());
        iyzicoRequest.setBasketItems(List.of(basketItem));

        try {
            paymentService.attachProviderSubmerchantKey(payment, worker.getIyzicoSubmerchantKey());
        } catch (RuntimeException exception) {
            paymentMetrics.initiationPersistenceError();
            paymentService.failInitiation(payment.getId());
            throw exception;
        }

        PaymentSessionResponseDto paymentSession;
        try {
            paymentSession = iyzicoPaymentService.initiateCheckoutFormPayment(iyzicoRequest);
        } catch (ExternalServiceException exception) {
            paymentMetrics.initiationProviderError();
            if (PaymentMessages.PROVIDER_REJECTED.equals(exception.getMessage())) {
                paymentService.failInitiation(payment.getId());
            } else {
                paymentService.markInitiationUncertain(payment.getId());
            }
            throw exception;
        } catch (RuntimeException exception) {
            paymentMetrics.initiationProviderError();
            paymentService.markInitiationUncertain(payment.getId());
            throw exception;
        }
        try {
            paymentService.attachProviderToken(payment, paymentSession.getToken());
        } catch (RuntimeException exception) {
            paymentMetrics.initiationPersistenceError();
            paymentService.markInitiationUncertain(payment.getId());
            throw exception;
        }
        paymentMetrics.initiationSucceeded();

        log.info("Payment session initiated for job assignment with id {} and payment {}", assignmentId, payment.getId());
        PaymentSessionDto response = jobAssignmentMapper
                .tojobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto(paymentSession);
        response.setPricing(pricingService.describe(assignment));
        return response;

    }

    private void fillBuyerAndAddressInfo(PaymentSessionRequestDto iyzicoRequest, Job job, String clientIpAddress, User currentUser) {
        IyzicoBuyerDto buyer = new IyzicoBuyerDto();
        IyzicoAddressDto billingAddress = new IyzicoAddressDto();

        IyzicoAddressDto shippingAddress = new IyzicoAddressDto();

        if (job.getAddress() != null){
            shippingAddress.setAddress(AddressFormatter.displayAddress(job.getAddress()));
            shippingAddress.setCity(job.getAddress().getCity().getName());
            shippingAddress.setCountry(job.getAddress().getCountry().getName());
            shippingAddress.setZipCode(job.getAddress().getPostalCode());
            shippingAddress.setContactName(currentUser.getFirstName() + " " + currentUser.getLastName());
        }else{
            throw new ValidationException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_JOB);
        }


        if (job.getEmployerType() == JobEmployerType.COMPANY) {
            Company company = job.getCompany();
            buyer.setId(company.getId().toString());
            buyer.setName(company.getName());
            buyer.setSurname("."); // Company does not have a surname
            buyer.setGsmNumber(company.getPhoneNumber());
            buyer.setEmail(company.getEmail());
            buyer.setIdentityNumber(company.getTaxNumber()); //VKN

            if (company.getAddress() != null) {
                billingAddress.setAddress(AddressFormatter.displayAddress(company.getAddress()));
                billingAddress.setCity(company.getAddress().getCity().getName());
                billingAddress.setCountry(company.getAddress().getCountry().getName());
                billingAddress.setZipCode(company.getAddress().getPostalCode());
                billingAddress.setContactName(company.getName());
                buyer.setRegistrationAddress(billingAddress.getAddress()+", "+ billingAddress.getCity()+", "+billingAddress.getCountry()+ ", "+ billingAddress.getZipCode());
                buyer.setCity(billingAddress.getCity());
                buyer.setCountry(billingAddress.getCountry());
            }else {
                throw new ValidationException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_EMPLOYER);
            }
        }else {
            User individualEmployer = job.getCreator();
            buyer.setId(individualEmployer.getId().toString());
            buyer.setName(individualEmployer.getFirstName());
            buyer.setSurname(individualEmployer.getLastName());
            buyer.setGsmNumber(individualEmployer.getPhoneNumber());
            buyer.setEmail(individualEmployer.getEmail());
            buyer.setIdentityNumber(individualEmployer.getTcIdentityNumber());

            Address employerAddress = individualEmployer.getAddress();
            if (employerAddress != null) {
                billingAddress.setAddress(AddressFormatter.displayAddress(employerAddress));
                billingAddress.setCity(employerAddress.getCity().getName());
                billingAddress.setCountry(employerAddress.getCountry().getName());
                billingAddress.setZipCode(employerAddress.getPostalCode());
                billingAddress.setContactName(individualEmployer.getFirstName() + " " + individualEmployer.getLastName());
                buyer.setRegistrationAddress(billingAddress.getAddress()+", "+ billingAddress.getCity()+", "+billingAddress.getCountry()+ ", "+ billingAddress.getZipCode());
                buyer.setCity(billingAddress.getCity());
                buyer.setCountry(billingAddress.getCountry());

            }else {
                throw new ValidationException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_EMPLOYER);
            }
        }

        buyer.setId(currentUser.getId().toString());

        iyzicoRequest.setBuyer(buyer);
        iyzicoRequest.setShippingAddress(shippingAddress);
        iyzicoRequest.setBillingAddress(billingAddress);
    }

    private SubMerchantCreateRequestDto createSubMerchantDtoForWorker(User worker) {
        SubMerchantCreateRequestDto merchantDto = new SubMerchantCreateRequestDto();
        merchantDto.setSubMerchantExternalId(worker.getId().toString());
        merchantDto.setName(worker.getFirstName() + " " + worker.getLastName());
        merchantDto.setEmail(worker.getEmail());
        merchantDto.setGsmNumber(worker.getPhoneNumber());
        if (worker.getAddress() != null) {
            merchantDto.setAddress(AddressFormatter.displayAddress(worker.getAddress()));
        }else {
            throw new ValidationException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_WORKER);
        }
        BankAccount workerAccount = bankAccountService.findPrimaryAccount(worker.getId())
                .orElseThrow(() -> new ValidationException(JobAssignmentMessages.IBAN_NOT_PROVIDED_FOR_WORKER));
        merchantDto.setIban(workerAccount.getIban());
        merchantDto.setSubMerchantType(SubMerchantType.PERSONAL);
        if (worker.isIdentityVerified()){
            merchantDto.setIdentityNumber(worker.getTcIdentityNumber());
        }else {
            throw new ValidationException(JobAssignmentMessages.WORKER_IDENTITY_NOT_VERIFIED);
        }
        merchantDto.setContactName(worker.getFirstName());
        merchantDto.setContactSurname(worker.getLastName());
        return merchantDto;
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<MyAssignmentDto> getMyAssignments(boolean asEmployer, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, MY_ASSIGNMENTS_SORTABLE);
        Pageable pageable = pageableSanitizer.sanitize(requested, MY_ASSIGNMENTS_SORTABLE,
                Sort.by(Sort.Direction.DESC, "assignmentDateTime"));

        UUID userId = securityService.getAuthenticatedUserId();
        Page<JobAssignment> page = asEmployer
                ? jobAssignmentDao.findAllByJobCreatorId(userId, pageable)
                : jobAssignmentDao.findAllByWorkerId(userId, pageable);

        return toPage(page, userId,
                assignment -> !asEmployer && LIVE_ASSIGNMENT_STATUSES.contains(assignment.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<MyAssignmentDto> getAssignmentsForJob(UUID jobId, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, MY_ASSIGNMENTS_SORTABLE);
        Pageable pageable = pageableSanitizer.sanitize(requested, MY_ASSIGNMENTS_SORTABLE,
                Sort.by(Sort.Direction.DESC, "assignmentDateTime"));

        Job job = jobService.getById(jobId);
        UUID actorId = securityService.getAuthenticatedUserId();
        boolean owner = job.getCreator() != null && job.getCreator().getId().equals(actorId);
        boolean companyContact = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), actorId);
        if (!owner && !companyContact) {
            throw Concealment.notFound(JobMessages.JOB_NOT_FOUND, actorId, jobId, "not the employer of the job");
        }

        return toPage(jobAssignmentDao.findAllByJobId(jobId, pageable), actorId, assignment -> false);
    }

    @Override
    @Transactional(readOnly = true)
    public MyAssignmentDto getMyAssignment(UUID assignmentId) {
        JobAssignment assignment = getDetailedById(assignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();
        CancellationParty party = participants.partyOf(assignment, actorId)
                .orElseThrow(() -> Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                        "not a party of the assignment"));
        boolean activeWorker = party == CancellationParty.WORKER
                && LIVE_ASSIGNMENT_STATUSES.contains(assignment.getStatus());
        return toMyAssignmentDto(assignment, actorId, activeWorker);
    }

    private PageDto<MyAssignmentDto> toPage(Page<JobAssignment> page, UUID viewerId,
                                            Predicate<JobAssignment> activeWorker) {
        LinkedRecords linked = linkedRecordsOf(page.getContent());
        List<MyAssignmentDto> content = page.getContent().stream()
                .map(assignment -> toMyAssignmentDto(assignment, linked, viewerId, activeWorker.test(assignment)))
                .toList();
        galleryService.fillJobCovers(content.stream().map(MyAssignmentDto::job).toList());
        return PageDto.of(page, content);
    }

    private MyAssignmentDto toMyAssignmentDto(JobAssignment assignment, UUID viewerId, boolean activeWorker) {
        MyAssignmentDto dto = toMyAssignmentDto(assignment, linkedRecordsOf(List.of(assignment)), viewerId,
                activeWorker);
        galleryService.fillJobCovers(List.of(dto.job()));
        return dto;
    }

    private MyAssignmentDto toMyAssignmentDto(JobAssignment assignment, LinkedRecords linked, UUID viewerId,
                                              boolean activeWorker) {
        UUID id = assignment.getId();
        MyAssignmentDto dto = jobAssignmentMapper.toMyAssignmentDto(assignment,
                linked.cancellations().get(id), linked.payouts().get(id), linked.paymentSecuredAt().get(id),
                viewerFactsOf(assignment, linked.reviews().getOrDefault(id, List.of()), viewerId));
        addressVisibility.protect(assignment.getJob(), dto.job(), viewerId, activeWorker);
        return dto;
    }

    private JobAssignmentMapper.ViewerFacts viewerFactsOf(JobAssignment assignment, List<Review> reviews,
                                                          UUID viewerId) {
        UUID workerId = assignment.getWorker() == null ? null : assignment.getWorker().getId();
        boolean workerView = workerId != null && workerId.equals(viewerId);

        Review myReview = null;
        boolean counterpartyReviewed = false;
        for (Review review : reviews) {
            UUID authorId = review.getAuthor().getId();
            if (authorId.equals(viewerId)) {
                myReview = review;
            } else if (authorId.equals(workerId) != workerView) {
                counterpartyReviewed = true;
            }
        }

        PricingBreakdownDto pricing = pricingDtoMapper.toPricing(assignment);
        if (workerView) {
            return new JobAssignmentMapper.ViewerFacts(null, pricing == null ? null : pricing.workerView(),
                    myReview, counterpartyReviewed);
        }
        return new JobAssignmentMapper.ViewerFacts(assignment.getTotalCharged(), pricing, myReview,
                counterpartyReviewed);
    }

    private LinkedRecords linkedRecordsOf(List<JobAssignment> assignments) {
        if (assignments.isEmpty()) {
            return new LinkedRecords(Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<UUID> ids = assignments.stream().map(JobAssignment::getId).toList();

        Map<UUID, Cancellation> cancellations = new HashMap<>();
        for (Cancellation cancellation : cancellationDao.findAllByJobAssignmentIdIn(ids)) {
            cancellations.put(cancellation.getJobAssignment().getId(), cancellation);
        }

        Map<UUID, Payout> payouts = new HashMap<>();
        for (Payout payout : payoutService.findForAssignments(ids)) {
            payouts.put(payout.getJobAssignment().getId(), payout);
        }

        Map<UUID, List<Review>> reviews = new HashMap<>();
        for (Review review : reviewDao.findAllByJobAssignmentIdIn(ids)) {
            reviews.computeIfAbsent(review.getJobAssignment().getId(), key -> new ArrayList<>()).add(review);
        }

        return new LinkedRecords(cancellations, payouts, paymentService.securedAtForAssignments(ids), reviews);
    }

    private record LinkedRecords(Map<UUID, Cancellation> cancellations, Map<UUID, Payout> payouts,
                                 Map<UUID, Instant> paymentSecuredAt, Map<UUID, List<Review>> reviews) {
    }

    @Override
    @Transactional
    public MyAssignmentDto completeAssignment(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (participants.partyOf(assignment, actorId)
                .filter(party -> party == CancellationParty.EMPLOYER).isEmpty()) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                    "not the employer of the assignment");
        }

        if (assignment.getStatus() != AssignmentStatus.CONFIRMED
                && assignment.getStatus() != AssignmentStatus.IN_PROGRESS) {
            throw new ConflictException(JobAssignmentMessages.NOT_COMPLETABLE);
        }

        if (assignment.getPaymentStatus() != PaymentStatus.HELD_IN_ESCROW) {
            log.warn("Completion refused on assignment {}: money is not in escrow, payment status is {}",
                    assignmentId, assignment.getPaymentStatus());
            throw new ConflictException(JobAssignmentMessages.PAYMENT_NOT_SECURED);
        }

        JobAssignment saved = markCompleted(assignment);
        boolean released = payoutService.releaseToWorkerUnlessDisputed(assignmentId,
                PayoutReleaseOrigin.EMPLOYER_COMPLETION);

        log.info("Assignment {} marked completed by employer {}, escrow released: {}",
                assignmentId, actorId, released);
        activityNotificationService.assignmentCompleted(assignment);
        return toMyAssignmentDto(saved, actorId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAutoCompletableAssignmentIds() {
        int graceDays = settingsService.intValue(SettingKeys.AUTO_COMPLETION_GRACE_DAYS,
                autoCompletionProperties.getGraceDays());

        LocalDateTime endedBefore = LocalDateTime.now(autoCompletionProperties.getZoneId())
                .minusDays(graceDays);

        return jobAssignmentDao.findAutoCompletableIds(
                ACTIVE_WORK_STATUSES,
                PaymentStatus.HELD_IN_ESCROW,
                endedBefore,
                PageRequest.of(0, autoCompletionProperties.getBatchSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUnpaidExpiringAssignmentIds() {
        int hours = settingsService.intValue(SettingKeys.UNPAID_EXPIRY_HOURS_BEFORE_START,
                unpaidExpiryProperties.getHoursBeforeStart());

        LocalDateTime startingBefore = LocalDateTime.now(unpaidExpiryProperties.getZoneId())
                .plusHours(hours);

        return jobAssignmentDao.findUnpaidExpiringIds(
                AssignmentStatus.CONFIRMED,
                List.of(PaymentStatus.PENDING_COLLECTION, PaymentStatus.FAILED),
                startingBefore,
                PageRequest.of(0, unpaidExpiryProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean autoCompleteAssignment(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(assignmentId)
                .orElse(null);

        if (assignment == null
                || (assignment.getStatus() != AssignmentStatus.CONFIRMED
                    && assignment.getStatus() != AssignmentStatus.IN_PROGRESS)
                || assignment.getPaymentStatus() != PaymentStatus.HELD_IN_ESCROW) {
            return false;
        }

        markCompleted(assignment);
        payoutService.releaseToWorker(assignmentId, PayoutReleaseOrigin.AUTO_COMPLETION);

        log.info("Assignment {} auto completed after the grace period and escrow released.", assignmentId);
        return true;
    }

    private JobAssignment markCompleted(JobAssignment assignment) {
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setActualCompletionDateTime(Instant.now());

        JobAssignment saved = jobAssignmentDao.save(assignment);
        badgeService.evaluate(assignment.getWorker().getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public JobAssignment getDetailedById(UUID assignmentId) {
        return jobAssignmentDao.findWithDetailsForPaymentById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
    }

    @Override
    @Transactional
    public JobAssignment getDetailedByIdForUpdate(UUID assignmentId) {
        jobAssignmentDao.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
        return jobAssignmentDao.findWithDetailsForPaymentById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAssignmentDto> listForStaff(String query, UUID workerId, UUID employerId, UUID jobId,
                                                 Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, ADMIN_LIST_SORTABLE);
        return jobAssignmentDao.findAllForStaff(StaffSearchQuery.normalize(query), workerId, employerId, jobId,
                        pageableSanitizer.sanitize(requested, ADMIN_LIST_SORTABLE, ADMIN_LIST_SORT))
                .map(jobAssignmentMapper::toAdminAssignmentDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobAssignment> findLatestByJobAndWorker(UUID jobId, UUID workerId) {
        return jobAssignmentDao.findAllByJobIdAndWorkerId(jobId, workerId).stream().max(OLDEST_FIRST);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobAssignment> findLatestByJobsAndWorkers(Collection<UUID> jobIds, Collection<UUID> workerIds) {
        if (jobIds.isEmpty() || workerIds.isEmpty()) {
            return List.of();
        }
        Map<Map.Entry<UUID, UUID>, JobAssignment> latest = new HashMap<>();
        for (JobAssignment assignment : jobAssignmentDao.findAllByJobIdInAndWorkerIdIn(jobIds, workerIds)) {
            latest.merge(Map.entry(assignment.getJob().getId(), assignment.getWorker().getId()), assignment,
                    BinaryOperator.maxBy(OLDEST_FIRST));
        }
        return List.copyOf(latest.values());
    }

    @Override
    @Transactional(readOnly = true)
    public long countOccupiedSlots(UUID jobId) {
        return jobAssignmentDao.countByJobIdAndStatusIn(jobId, SLOT_OCCUPYING_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedForWorker(UUID workerId) {
        return jobAssignmentDao.countByWorkerIdAndStatus(workerId, AssignmentStatus.COMPLETED);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedForEmployer(UUID employerId) {
        return jobAssignmentDao.countByJobCreatorIdAndStatus(employerId, AssignmentStatus.COMPLETED);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAsWorker(UUID workerId) {
        return jobAssignmentDao.countByWorkerId(workerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAsEmployer(UUID employerId) {
        return jobAssignmentDao.countByJobCreatorId(employerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyLink> findEscrowLinksForUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jobAssignmentDao.findMoneyLinksForUsers(userIds, PaymentStatus.HELD_IN_ESCROW);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyLink> findActiveWorkLinksForUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jobAssignmentDao.findActiveLinksForUsers(userIds, ACTIVE_WORK_STATUSES);
    }
}
