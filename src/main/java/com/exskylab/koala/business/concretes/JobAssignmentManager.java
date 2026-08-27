package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.dtos.jobAssignment.request.JobAssignmentsAssignmentIdStatusPatchRequestDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentsAssignmentIdStatusPatchResponseDto;
import com.exskylab.koala.core.exceptions.AssignmentAlreadyAnsweredException;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.UserNotWorkerForAssignmentException;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.*;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.entities.CancellationParty;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobAssignmentManager implements JobAssignmentService {

    private static final java.util.Set<String> MY_ASSIGNMENTS_SORTABLE =
            java.util.Set.of("assignmentDateTime", "actualCompletionDateTime", "status");


    private final JobAssignmentDao jobAssignmentDao;

    private final Logger logger = LoggerFactory.getLogger(JobAssignmentManager.class);
    private final SecurityService securityService;
    private final JobAssignmentMapper jobAssignmentMapper;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final CompanyContactService companyContactService;
    private final UserService userService;
    private final CommissionCalculator commissionCalculator;
    private final PaymentService paymentService;
    private final PaymentStateMachine paymentStateMachine;
    private final AppProperties appProperties;
    private final BankAccountService bankAccountService;
    private final PageableSanitizer pageableSanitizer;
    private final AssignmentParticipants participants;
    private final BadgeService badgeService;


    public JobAssignmentManager(JobAssignmentDao jobAssignmentDao, SecurityService securityService, JobAssignmentMapper jobAssignmentMapper, IyzicoPaymentService iyzicoPaymentService, CompanyContactService companyContactService, UserService userService,
                               CommissionCalculator commissionCalculator, PaymentService paymentService,
                               PaymentStateMachine paymentStateMachine, AppProperties appProperties,
                               BankAccountService bankAccountService, PageableSanitizer pageableSanitizer,
                               AssignmentParticipants participants, BadgeService badgeService) {
        this.jobAssignmentDao = jobAssignmentDao;
        this.securityService = securityService;
        this.jobAssignmentMapper = jobAssignmentMapper;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.companyContactService = companyContactService;
        this.userService = userService;
        this.commissionCalculator = commissionCalculator;
        this.paymentService = paymentService;
        this.paymentStateMachine = paymentStateMachine;
        this.appProperties = appProperties;
        this.bankAccountService = bankAccountService;
        this.pageableSanitizer = pageableSanitizer;
        this.participants = participants;
        this.badgeService = badgeService;
    }


    @Override
    public JobAssignment save(JobAssignment jobAssignment) {
        logger.info("Creating job assignment from application...");
        return jobAssignmentDao.save(jobAssignment);
    }

    @Override
    @Transactional
    public JobAssignmentsAssignmentIdStatusPatchResponseDto answerToAssignment(UUID assignmentId, JobAssignmentsAssignmentIdStatusPatchRequestDto jobAssignmentsAssignmentIdStatusPatchRequestDto) {
        logger.info("Answering to job assignment from");
        var assignment = jobAssignmentDao.findWithDetailsForPaymentById(assignmentId).orElseThrow(() -> {
            logger.info("Assignment with id {} not found", assignmentId);
            return new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND);
        });

        logger.info("Job assignment with id {} found", assignmentId);

        if (assignment.getStatus() != AssignmentStatus.PENDING_CONFIRMATION){
            logger.warn("Assignment with ID: {} is already answered.", assignment.getId());
            throw new AssignmentAlreadyAnsweredException(JobAssignmentMessages.ALREADY_ANSWERED);
        }

        var authenticatedUserFromContext = securityService.getAuthenticatedUser();

        if (!assignment.getWorker().getId().equals(authenticatedUserFromContext.getId())) {
            throw new UserNotWorkerForAssignmentException(JobAssignmentMessages.NOT_WORKER_FOR_ASSIGNMENT);
        }

        if (Boolean.TRUE.equals(jobAssignmentsAssignmentIdStatusPatchRequestDto.getAccepted())){
            assignment.setStatus(AssignmentStatus.CONFIRMED);
            assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
            logger.info("Job assignment with id {} has been accepted", assignmentId);
        }else{
            assignment.setStatus(AssignmentStatus.REJECTED);
            logger.info("Job assignment with id {} has been rejected", assignmentId);
        }

        assignment = jobAssignmentDao.save(assignment);

        logger.info("Job assignment with id {} has been saved", assignmentId);

        return jobAssignmentMapper.toJobAssignmentsAssignmentIdStatusPatchResponseDto(assignment);

    }


    @Transactional
    @Override
    public JobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto initiatePaymentSession(UUID assignmentId, String clientIpAddress) {
        logger.info("Initiating job assignment with id {}", assignmentId);
        var assignment = jobAssignmentDao.findById(assignmentId).orElseThrow(() -> {
            logger.info("Assignment with id {} not found", assignmentId);
            return new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND);
        });

        logger.info("Job assignment with id {} found", assignmentId);


        if (!paymentStateMachine.canInitiate(assignment)){
            logger.warn("Assignment with ID: {} is not in a state to initiate payment. Status: {}, PaymentStatus: {}", assignment.getId(), assignment.getStatus(), assignment.getPaymentStatus());
            throw new IllegalStateException(JobAssignmentMessages.ASSIGNMENT_NOT_READY_FOR_PAYMENT);
        }

        var currentUser = securityService.getAuthenticatedUser();
        var worker = assignment.getWorker();
        var job = assignment.getJob();
        var creator = job.getCreator();

        switch (job.getEmployerType()){
            case INDIVIDUAL -> {
               if (!currentUser.getId().equals(creator.getId())) {
                     logger.warn("User with id {} is not the creator of the job with id {}", currentUser.getId(), job.getId());
                     throw new UserNotWorkerForAssignmentException(JobAssignmentMessages.NOT_CREATOR_OF_JOB);
               }
               break;
            }
            case COMPANY -> {
                boolean isContact = companyContactService.isUserAContactOfCompany(job.getCompany().getId(), currentUser.getId());
                if (!isContact) {
                    logger.warn("User with id {} is not a contact of the company with id {}", currentUser.getId(), job.getCompany().getId());
                    throw new UserNotWorkerForAssignmentException(JobAssignmentMessages.USER_IS_NOT_A_CONTACT_OF_COMPANY);
                }
            }
            default -> {
                logger.error("Unknown employer type for job with id {}", job.getId());
                throw new IllegalArgumentException("Unknown employer type");
            }
        }

        if (worker.getIyzicoSubmerchantKey() == null || worker.getIyzicoSubmerchantKey().isBlank()){
            logger.info("Worker {} does not have an iyzico submerchant key. Creating one...", worker.getId());

            SubMerchantCreateRequestDto merchantDto = createSubMerchantDtoForWorker(worker);
            SubMerchantCreateResponseDto response = iyzicoPaymentService.createSubMerchant(merchantDto);

            worker.setIyzicoSubmerchantKey(response.getSubMerchantKey());
            userService.save(worker);
            logger.info("Worker {} has been created with iyzico submerchant key.}", worker.getId());
        }


        CommissionCalculator.Breakdown breakdown = commissionCalculator.calculate(job.getSalary());
        Payment payment = paymentService.startAttempt(assignment, breakdown);

        PaymentSessionRequestDto iyzicoRequest = new PaymentSessionRequestDto();

        iyzicoRequest.setConversationId(payment.getConversationId());
        iyzicoRequest.setPrice(breakdown.totalCharged().doubleValue());
        iyzicoRequest.setPaidPrice(breakdown.totalCharged().doubleValue());
        iyzicoRequest.setCurrency("TRY");
        iyzicoRequest.setBasketId(assignment.getId().toString());
        iyzicoRequest.setPaymentGroup("PRODUCT");
        iyzicoRequest.setCallbackUrl(appProperties.apiUrl() + "/payments/callback");

        fillBuyerAndAddressInfo(iyzicoRequest, job, clientIpAddress, currentUser);

        IyzicoBasketItemDto basketItem = new IyzicoBasketItemDto();
        basketItem.setId(job.getId().toString());
        basketItem.setName(job.getTitle());
        basketItem.setPrice(breakdown.totalCharged().doubleValue());
        basketItem.setSubMerchantKey(worker.getIyzicoSubmerchantKey());
        basketItem.setSubMerchantPrice(breakdown.workerPayout().doubleValue());
        iyzicoRequest.setBasketItems(List.of(basketItem));

        PaymentSessionResponseDto paymentSession = iyzicoPaymentService.initiateCheckoutFormPayment(iyzicoRequest);
        paymentService.attachProviderToken(payment, paymentSession.getToken());

        logger.info("Payment session initiated for job assignment with id {} and payment {}", assignmentId, payment.getId());
        return jobAssignmentMapper.tojobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto(paymentSession);

    }

    private void fillBuyerAndAddressInfo(PaymentSessionRequestDto iyzicoRequest, Job job, String clientIpAddress, User currentUser) {
        IyzicoBuyerDto buyer = new IyzicoBuyerDto();
        IyzicoAddressDto billingAddress = new IyzicoAddressDto();

        IyzicoAddressDto shippingAddress = new IyzicoAddressDto();

        if (job.getAddress() != null){
            shippingAddress.setAddress(job.getAddress().getAddressLine());
            shippingAddress.setCity(job.getAddress().getCity().getName());
            shippingAddress.setCountry(job.getAddress().getCountry().getName());
            shippingAddress.setZipCode(job.getAddress().getPostalCode());
            shippingAddress.setContactName(currentUser.getFirstName() + " " + currentUser.getLastName());
        }else{
            throw new IllegalArgumentException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_JOB);
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
                billingAddress.setAddress(company.getAddress().getAddressLine());
                billingAddress.setCity(company.getAddress().getCity().getName());
                billingAddress.setCountry(company.getAddress().getCountry().getName());
                billingAddress.setZipCode(company.getAddress().getPostalCode());
                billingAddress.setContactName(company.getName());
                buyer.setRegistrationAddress(billingAddress.getAddress()+", "+ billingAddress.getCity()+", "+billingAddress.getCountry()+ ", "+ billingAddress.getZipCode());
                buyer.setCity(billingAddress.getCity());
                buyer.setCountry(billingAddress.getCountry());
            }else {
                throw new IllegalArgumentException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_EMPLOYER);
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
                billingAddress.setAddress(employerAddress.getAddressLine());
                billingAddress.setCity(employerAddress.getCity().getName());
                billingAddress.setCountry(employerAddress.getCountry().getName());
                billingAddress.setZipCode(employerAddress.getPostalCode());
                billingAddress.setContactName(individualEmployer.getFirstName() + " " + individualEmployer.getLastName());
                buyer.setRegistrationAddress(billingAddress.getAddress()+", "+ billingAddress.getCity()+", "+billingAddress.getCountry()+ ", "+ billingAddress.getZipCode());
                buyer.setCity(billingAddress.getCity());
                buyer.setCountry(billingAddress.getCountry());

            }else {
                throw new IllegalArgumentException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_EMPLOYER);
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
            merchantDto.setAddress(worker.getAddress().getAddressLine()+", "+ worker.getAddress().getCity().getName()+", "+worker.getAddress().getCountry().getName()+ ", "+ worker.getAddress().getPostalCode());
        }else {
            throw new IllegalArgumentException(JobAssignmentMessages.ADDRESS_NOT_PROVIDED_FOR_WORKER);
        }
        merchantDto.setIban(bankAccountService.findPrimaryIban(worker.getId())
                .orElseThrow(() -> new IllegalArgumentException(JobAssignmentMessages.IBAN_NOT_PROVIDED_FOR_WORKER)));
        merchantDto.setSubMerchantType(SubMerchantType.PERSONAL);
        if (worker.isIdentityVerified()){
            merchantDto.setIdentityNumber(worker.getTcIdentityNumber());
        }else {
            throw new IllegalArgumentException(JobAssignmentMessages.WORKER_IDENTITY_NOT_VERIFIED);
        }
        merchantDto.setContactName(worker.getFirstName());
        merchantDto.setContactSurname(worker.getLastName());
        return merchantDto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobAssignment> getMyAssignments(boolean asEmployer, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, MY_ASSIGNMENTS_SORTABLE);
        Pageable pageable = pageableSanitizer.sanitize(requested, MY_ASSIGNMENTS_SORTABLE,
                Sort.by(Sort.Direction.DESC, "assignmentDateTime"));

        UUID userId = securityService.getAuthenticatedUserId();
        return asEmployer
                ? jobAssignmentDao.findAllByJobCreatorId(userId, pageable)
                : jobAssignmentDao.findAllByWorkerId(userId, pageable);
    }

    @Override
    @Transactional
    public JobAssignment completeAssignment(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));

        if (participants.partyOf(assignment, securityService.getAuthenticatedUserId())
                .filter(party -> party == CancellationParty.EMPLOYER).isEmpty()) {
            throw new ResourceNotFoundException(JobAssignmentMessages.ONLY_EMPLOYER_CAN_COMPLETE);
        }

        if (assignment.getStatus() != AssignmentStatus.CONFIRMED
                && assignment.getStatus() != AssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException(JobAssignmentMessages.NOT_COMPLETABLE);
        }

        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setActualCompletionDateTime(Instant.now());

        JobAssignment saved = jobAssignmentDao.save(assignment);
        badgeService.evaluate(assignment.getWorker().getId());

        logger.info("Assignment {} marked completed by employer {}", assignmentId,
                securityService.getAuthenticatedUserId());
        return saved;
    }
}
