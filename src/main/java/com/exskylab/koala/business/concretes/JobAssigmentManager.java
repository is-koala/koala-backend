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
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.UserNotWorkerForAssignmentException;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.*;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobAssigmentManager implements JobAssignmentService {

    private final JobAssignmentDao jobAssignmentDao;

    private final Logger logger = LoggerFactory.getLogger(JobAssigmentManager.class);
    private final SecurityService securityService;
    private final JobAssignmentMapper jobAssignmentMapper;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final CompanyContactService companyContactService;
    private final UserService userService;


    public JobAssigmentManager(JobAssignmentDao jobAssignmentDao, SecurityService securityService, JobAssignmentMapper jobAssignmentMapper, IyzicoPaymentService iyzicoPaymentService, CompanyContactService companyContactService, UserService userService) {
        this.jobAssignmentDao = jobAssignmentDao;
        this.securityService = securityService;
        this.jobAssignmentMapper = jobAssignmentMapper;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.companyContactService = companyContactService;
        this.userService = userService;
    }


    @Override
    public JobAssignment save(JobAssignment jobAssignment) {
        logger.info("Creating job assignment from application...");
        return jobAssignmentDao.save(jobAssignment);
    }

    @Override
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

        var authenticatedUserFromContext = securityService.getAuthenticatedUserFromContext();

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


        if (assignment.getStatus() != AssignmentStatus.CONFIRMED || assignment.getPaymentStatus() != PaymentStatus.PENDING_COLLECTION){
            logger.warn("Assignment with ID: {} is not in a state to initiate payment. Status: {}, PaymentStatus: {}", assignment.getId(), assignment.getStatus(), assignment.getPaymentStatus());
            throw new IllegalStateException(JobAssignmentMessages.ASSIGNMENT_NOT_READY_FOR_PAYMENT);
        }

        var currentUser = securityService.getAuthenticatedUserFromDatabase();
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


        double workerPayout = job.getSalary();
        double koalaCommission = calculateCommission(workerPayout);
        double totalPriceToCharge = workerPayout+koalaCommission;

        PaymentSessionRequestDto iyzicoRequest = new PaymentSessionRequestDto();

        iyzicoRequest.setConversationId(assignmentId.toString());
        iyzicoRequest.setPrice(totalPriceToCharge);
        iyzicoRequest.setPaidPrice(totalPriceToCharge);
        iyzicoRequest.setCurrency("TRY");
        iyzicoRequest.setBasketId(assignmentId.toString());
        iyzicoRequest.setPaymentGroup("PRODUCT");
        iyzicoRequest.setCallbackUrl("https://iskoala.com/payment/callback"); // will implement

        fillBuyerAndAddressInfo(iyzicoRequest, job, clientIpAddress, currentUser);

        IyzicoBasketItemDto basketItem = new IyzicoBasketItemDto();
        basketItem.setId(job.getId().toString());
        basketItem.setName(job.getTitle());
        basketItem.setPrice(totalPriceToCharge);
        basketItem.setSubMerchantKey(worker.getIyzicoSubmerchantKey());
        basketItem.setSubMerchantPrice(workerPayout);
        iyzicoRequest.setBasketItems(List.of(basketItem));

        PaymentSessionResponseDto paymentSession = iyzicoPaymentService.initiateCheckoutFormPayment(iyzicoRequest);

        jobAssignmentDao.save(assignment);
        logger.info("Payment session initiated and saved for job assignment with id {}", assignmentId);
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
        if (worker.getIban() != null && !worker.getIban().isBlank()) {
            merchantDto.setIban(worker.getIban());
        }else {
            throw new IllegalArgumentException(JobAssignmentMessages.IBAN_NOT_PROVIDED_FOR_WORKER);
        }
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

    private double calculateCommission(double workerPayout) {
        return workerPayout*7.5/100;
    }
}
