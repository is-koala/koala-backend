package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.entities.*;
import jakarta.persistence.EntityManager;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CompanyManager implements CompanyService {

    private final CompanyDao companyDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ImageService imageService;
    private final CompanyContactInvitationService companyContactInvitationService;
    private final SecurityService securityService;
    private final AddressService addressService;

    private final EntityManager entityManager;
    private final CompanyMapper companyMapper;
    private final CompanyContactService companyContactService;
    private final GalleryService galleryService;
    private final CompanyFollowService companyFollowService;
    

    public CompanyManager(CompanyDao companyDao, UserService userService,
                          NotificationService notificationService, ImageService imageService,
                          CompanyContactInvitationService companyContactInvitationService,
                          SecurityService securityService, AddressService addressService,
                          EntityManager entityManager, CompanyMapper companyMapper,
                          CompanyContactService companyContactService, GalleryService galleryService,
                          CompanyFollowService companyFollowService) {
        this.companyDao = companyDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.imageService = imageService;
        this.companyContactInvitationService = companyContactInvitationService;
        this.securityService = securityService;
        this.addressService = addressService;
        this.entityManager = entityManager;
        this.companyMapper = companyMapper;
        this.companyContactService = companyContactService;
        this.galleryService = galleryService;
        this.companyFollowService = companyFollowService;
    }

    @Override
    @Transactional
    public CompanyDto addCompany(CreateCompanyRequestDto createCompanyRequestDto, MultipartFile logo) {
        CompanyType companyType;
        try {
            companyType = CompanyType.valueOf(createCompanyRequestDto.getType());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(CompanyMessages.INVALID_COMPANY_TYPE);
        }

        log.info("Adding new company with name: {}", createCompanyRequestDto.getName());
        var user = securityService.getAuthenticatedUser();
        log.info("User with id: {} is adding a new company.", user.getId());

        if (!user.isIdentityVerified()){
            throw new ValidationException(CompanyMessages.USER_MUST_VERIFY_IDENTITY);
        }

        Address address = addressService.createAddress(createCompanyRequestDto.getAddress());
        log.info("Address created for company with addressId: {}", address.getId());



        var company = new Company();
        company.setName(createCompanyRequestDto.getName());
        company.setEmail(createCompanyRequestDto.getEmail());
        company.setPhoneNumber(createCompanyRequestDto.getPhoneNumber());
        company.setWebsite(createCompanyRequestDto.getWebsite());
        company.setDescription(createCompanyRequestDto.getDescription());
        company.setTaxNumber(createCompanyRequestDto.getTaxNumber());
        company.setType(companyType);
        company.setAddress(address);
        company.setApproved(false);

        if (logo != null && !logo.isEmpty()) {
            var image = imageService.uploadImage(logo);
            company.setLogo(image);
            log.info("Logo uploaded for company with id: {}", company.getId());
        }
        log.info("New company with id: {} added successfully.", company.getId());


        var companyContact = new CompanyContact();
        companyContact.setRole(CompanyContactRole.ADMIN);
        companyContact.setUser(user);

        company.addContact(companyContact);

        var savedCompany = companyDao.save(company);
        log.info("Company contact created for user with id: {} and company with id: {}", user.getId(), company.getId());

        entityManager.flush();

        entityManager.clear();


        sendCompanyCreatedMail(savedCompany, companyContact);

        log.info("Notification sent to user with id: {} for company with id: {}", user.getId(), company.getId());



        var returnCompany = companyDao.findById(savedCompany.getId()).orElseThrow(() -> {
            log.error("Company with id: {} not found after saving.", company.getId());
            return new ResourceNotFoundException(CompanyMessages.COMPANY_NOT_FOUND);
        });



        return companyMapper.toCompanyDto(returnCompany);

    }

    @Override
    @Transactional
    public void inviteContactToCompany(UUID companyId, InviteContactToCompanyDto contactToCompanyDto) {
        log.info("Inviting contact to company with id: {}", companyId);


        CompanyContactRole role;
        try {
            role = CompanyContactRole.valueOf(contactToCompanyDto.getRole());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(CompanyMessages.INVALID_COMPANY_CONTACT_ROLE);
        }


        var currentUser = securityService.getAuthenticatedUser();


        var company = companyDao.findById(companyId).orElseThrow(() -> {
            log.error("Company with id: {} not found.", companyId);
            return new ResourceNotFoundException(CompanyMessages.COMPANY_NOT_FOUND);
        });

        boolean isAdmin = company.getContacts().stream()
                .anyMatch(contact ->
                        contact.getUser().getId().equals(currentUser.getId()) &&
                        contact.getRole().equals(CompanyContactRole.ADMIN));

        if (!isAdmin){
            log.warn("User with id: {} is not an admin of company with id: {}, terminating request!", currentUser.getId(), companyId);
            throw new ForbiddenException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY);
        }

        var userToInvite = userService.getByEmail(contactToCompanyDto.getEmail());

        boolean isContactAlreadyExists = company.getContacts().stream()
                .anyMatch(contact -> contact.getUser().getId().equals(userToInvite.getId()));

        if (isContactAlreadyExists){
            log.warn("User with id: {} is already a contact of company with id: {}, terminating request!", userToInvite.getId(), companyId);
            throw new ValidationException(CompanyMessages.USER_ALREADY_A_CONTACT);
        }

        if (userToInvite.getTcIdentityNumber() == null) {
            throw new ValidationException(CompanyMessages.USER_MUST_VERIFY_IDENTITY);
        }

        if (!userToInvite.getTcIdentityNumber().equals(contactToCompanyDto.getTcIdentityNumber())){
            throw new ValidationException(CompanyMessages.USER_TC_IDENTITY_DOES_NOT_MATCH);
        }

        companyContactInvitationService.inviteUserToCompany(currentUser, userToInvite, company, role);

        log.info("Contact with user id: {} invited to company with id: {} successfully.", userToInvite.getId(), companyId);

    }

    @Override
    public Company getCompanyById(UUID companyId) {
        log.debug("Getting company with id: {}", companyId);

        return companyDao.findById(companyId).orElseThrow(() -> {
            log.error("Company with id: {} not found.", companyId);
            return new ResourceNotFoundException(CompanyMessages.COMPANY_NOT_FOUND);
        });
    }

    private void sendCompanyCreatedMail(Company savedCompany, CompanyContact contact ) {
        log.info("Sending company created email for company with id: {}", savedCompany.getId());

        var sendEmailDto = new SendEmailDto();
        sendEmailDto.setDestinationEmail(savedCompany.getEmail());
        sendEmailDto.setSubject("KOALA ŞİRKET OLUŞTURMA TALEBİNİZ ALINMIŞTIR");
        sendEmailDto.setTemplateName("company-created-to-company");
        sendEmailDto.setTemplateParameters(Map.of(
                "companyName", savedCompany.getName(),
                "companyId", savedCompany.getId().toString(),
                "companyEmail", savedCompany.getEmail()
        ));
        sendEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notificationService.sendEmail(sendEmailDto, DispatchPriority.NORMAL, true);
        log.info("Company created email queued for {}", LogMasks.email(sendEmailDto.getDestinationEmail()));

        var sendEmailDtoToContact = new SendEmailDto();
        sendEmailDtoToContact.setDestinationEmail(contact.getUser().getEmail());
        sendEmailDtoToContact.setSubject("KOALA ŞİRKET OLUŞTURMA TALEBİNİZ ALINMIŞTIR");
        sendEmailDtoToContact.setTemplateName("company-created-to-contact");
        sendEmailDtoToContact.setTemplateParameters(Map.of(
                "companyName", savedCompany.getName(),
                "companyId", savedCompany.getId().toString(),
                "contactFirstName", contact.getUser().getFirstName(),
                "contactLastName", contact.getUser().getLastName(),
                "contactId", contact.getId().toString(),
                "contactRole", contact.getRole().name(),
                "contactEmail", contact.getUser().getEmail()
        ));
        sendEmailDtoToContact.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notificationService.sendEmail(sendEmailDtoToContact, DispatchPriority.NORMAL, true);
        log.info("Company created email queued for contact {}", LogMasks.email(sendEmailDtoToContact.getDestinationEmail()));

        log.info("Company created emails sent successfully for company with id: {}", savedCompany.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyDto getCompanyDetail(UUID companyId) {
        CompanyDto company = companyMapper.toCompanyDto(getCompanyById(companyId));
        company.setFollowerCount(companyFollowService.followerCount(companyId));
        company.setFollowing(companyFollowService.isFollowing(companyId,
                securityService.getAuthenticatedUserId()));
        return company;
    }

    @Override
    @Transactional
    public CompanyDto updateCompany(UUID companyId, UpdateCompanyRequestDto request) {
        requireCompanyAdmin(companyId);
        Company company = getCompanyById(companyId);

        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getEmail() != null) {
            company.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            company.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }

        Company saved = companyDao.save(company);
        log.info("Company {} updated by user {}", companyId, securityService.getAuthenticatedUserId());
        return companyMapper.toCompanyDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyContact> getContacts(UUID companyId) {
        requireCompanyMember(companyId);
        return companyContactService.getContactsOfCompany(companyId);
    }

    @Override
    @Transactional
    public void removeContact(UUID companyId, UUID userId) {
        requireCompanyAdmin(companyId);

        CompanyContact contact = companyContactService.findContact(companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY));

        if (contact.getRole() == CompanyContactRole.ADMIN
                && companyContactService.countAdminsOfCompany(companyId) <= 1) {
            throw new ConflictException(CompanyMessages.LAST_ADMIN_CANNOT_BE_REMOVED);
        }

        companyContactService.removeContact(contact);
        log.info("Contact {} removed from company {}", userId, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Company> getCompaniesByApproval(boolean approved, Pageable pageable) {
        return companyDao.findAllByApproved(approved, pageable);
    }

    @Override
    @Transactional
    public Company approveCompany(UUID companyId) {
        Company company = getCompanyById(companyId);

        if (company.isApproved()) {
            throw new ConflictException(AdminMessages.COMPANY_ALREADY_APPROVED);
        }

        company.setApproved(true);
        Company saved = companyDao.save(company);
        log.info("Company {} approved by admin {}", companyId, securityService.getAuthenticatedUserId());
        return saved;
    }

    private void requireCompanyMember(UUID companyId) {
        if (!companyContactService.isUserAContactOfCompany(companyId, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY);
        }
    }

    @Override
    @Transactional
    public void followCompany(UUID companyId) {
        companyFollowService.follow(getCompanyById(companyId), securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public void unfollowCompany(UUID companyId) {
        companyFollowService.unfollow(companyId, securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<FollowedCompanyDto> getFollowedCompanies(Pageable pageable) {
        return companyFollowService.getFollowedCompanies(securityService.getAuthenticatedUserId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GalleryImageDto> getCompanyGallery(UUID companyId) {
        getCompanyById(companyId);
        return galleryService.getCompanyGallery(companyId);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> addCompanyImage(UUID companyId, MultipartFile file) {
        requireCompanyAdmin(companyId);
        return galleryService.addCompanyImage(getCompanyById(companyId), file);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> replaceCompanyGallery(UUID companyId, UpdateGalleryDto request) {
        requireCompanyAdmin(companyId);
        return galleryService.replaceCompanyGallery(getCompanyById(companyId), request);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> removeCompanyImage(UUID companyId, UUID imageId) {
        requireCompanyAdmin(companyId);
        return galleryService.removeCompanyImage(getCompanyById(companyId), imageId);
    }

    private void requireCompanyAdmin(UUID companyId) {
        if (!companyContactService.isUserAdminOfCompany(companyId, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY);
        }
    }
}
