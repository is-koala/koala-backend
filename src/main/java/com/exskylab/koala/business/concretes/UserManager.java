package com.exskylab.koala.business.concretes;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.user.request.UpdateUserDto;
import com.exskylab.koala.core.dtos.user.request.ChangePasswordDto;
import com.exskylab.koala.core.dtos.user.request.UpdateProfileDto;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.core.dtos.user.response.UserUpdateResponseDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.mappers.AddressMapper;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import com.exskylab.koala.business.abstracts.IdentityChallengeService;
import com.exskylab.koala.core.utilities.identity.CscaTrustStore;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationException;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationResult;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserManager implements UserService {

    private static final Set<String> USER_SORTABLE = Set.of("createdAt", "firstName", "lastName");

    private static final Sort USER_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private static final Locale TURKISH = Locale.of("tr", "TR");

    private final PageableSanitizer pageableSanitizer;
    private final UserDao userDao;
    private final EmailVerificationService emailVerificationService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final ImageService imageService;
    private final SecurityService securityService;
    private final AddressService addressService;
    private final AddressMapper addressMapper;
    private final UserProducerService userProducerService;
    private final BankAccountService bankAccountService;
    private final IdentityVerificationProperties identityVerificationProperties;
    private final CscaTrustStore cscaTrustStore;
    private final IdentityChallengeService identityChallengeService;
    private final PhoneVerificationService phoneVerificationService;
    private final BadgeService badgeService;
    private final BadgeMapper badgeMapper;
    private final ActivityNotificationService activityNotificationService;
    private final PasswordResetTokenService passwordResetTokenService;

    public UserManager(UserDao userDao,
                       EmailVerificationService emailVerificationService,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       SessionService sessionService,
                       ImageService imageService,
                       SecurityService securityService,
                       AddressService addressService,
                       AddressMapper addressMapper, UserProducerService userProducerService,
                       BankAccountService bankAccountService,
                       IdentityVerificationProperties identityVerificationProperties,
                       CscaTrustStore cscaTrustStore,
                       IdentityChallengeService identityChallengeService,
                       PhoneVerificationService phoneVerificationService,
                       BadgeService badgeService, BadgeMapper badgeMapper,
                       PageableSanitizer pageableSanitizer,
                       ActivityNotificationService activityNotificationService,
                       PasswordResetTokenService passwordResetTokenService) {
        this.userDao = userDao;
        this.emailVerificationService = emailVerificationService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.imageService = imageService;
        this.securityService = securityService;
        this.addressService = addressService;
        this.addressMapper = addressMapper;
        this.userProducerService = userProducerService;
        this.bankAccountService = bankAccountService;
        this.identityVerificationProperties = identityVerificationProperties;
        this.cscaTrustStore = cscaTrustStore;
        this.identityChallengeService = identityChallengeService;
        this.phoneVerificationService = phoneVerificationService;
        this.badgeService = badgeService;
        this.badgeMapper = badgeMapper;
        this.pageableSanitizer = pageableSanitizer;
        this.activityNotificationService = activityNotificationService;
        this.passwordResetTokenService = passwordResetTokenService;
    }

    @Override
    public User getByEmail(String email) {
        return userDao.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("{user.not.found.by.email}"));
    }

    @Override
    public User getById(UUID id) {
        return userDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("{user.not.found.by.id}"));
    }

    @Override
    public List<User> getAll() {
        return userDao.findAll();
    }

    @Override
    public User save(User user) {
        return userDao.save(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userDao.existsByEmail(email);
    }

    @Override
    public boolean existsById(UUID id) {
        return userDao.existsById(id);
    }

    @Override
    @Transactional
        public UserUpdateResponseDto patchCurrentUser(UpdateProfileDto userMePatchRequestDto) {
        log.info("Patching current user.");
        User currentUser = securityService.getAuthenticatedUser();

        List<UserVerification> pendingVerifications = new ArrayList<>();

        log.info("Patching user with ID: {}", currentUser.getId());

        if (userMePatchRequestDto.getFirstName() != null && userMePatchRequestDto.getFirstName().isEmpty()){
            throw new ValidationException(UserMessages.FIRST_NAME_CANNOT_BE_EMPTY);
        }

        if (userMePatchRequestDto.getLastName() != null && userMePatchRequestDto.getLastName().isEmpty()){
            throw new ValidationException(UserMessages.LAST_NAME_CANNOT_BE_EMPTY);
        }

        if (userMePatchRequestDto.getFirstName() != null) {
            if (currentUser.isIdentityVerified()){
                throw new ValidationException(UserMessages.IDENTITY_VERIFIED_CANNOT_CHANGE_FIRST_NAME);
            }
            log.info("Updating first name to: {}, from {}", userMePatchRequestDto.getFirstName(), currentUser.getFirstName());
            currentUser.setFirstName(userMePatchRequestDto.getFirstName());

        }

        if (userMePatchRequestDto.getLastName() != null) {
            if (currentUser.isIdentityVerified()){
                throw new ValidationException(UserMessages.IDENTITY_VERIFIED_CANNOT_CHANGE_LAST_NAME);
            }
            log.info("Updating last name to: {}, from {}", userMePatchRequestDto.getLastName(), currentUser.getLastName());
            currentUser.setLastName(userMePatchRequestDto.getLastName());
        }

        if (userMePatchRequestDto.getBirthdate() != null) {
            if (currentUser.isIdentityVerified()){
                throw new ValidationException(UserMessages.IDENTITY_VERIFIED_CANNOT_CHANGE_BIRTHDATE);
            }
            log.info("Updating birthdate to: {} from: {}", userMePatchRequestDto.getBirthdate(), currentUser.getBirthDate());
            currentUser.setBirthDate(userMePatchRequestDto.getBirthdate());
        }

        if (userMePatchRequestDto.getGender() != null){
            /*
            if (currentUser.isIdentityVerified()){
                throw new ValidationException(UserMessages.IDENTITY_VERIFIED_CANNOT_CHANGE_GENDER);
            }
             */
            Gender gender;
            try{
                gender = Gender.valueOf(userMePatchRequestDto.getGender().toUpperCase(Locale.ROOT));
            }catch (IllegalArgumentException e){
                throw new ValidationException(UserMessages.GENDER_NOT_FOUND);
            }
            log.info("Updating gender to: {}, from {}", userMePatchRequestDto.getGender(), currentUser.getGender());
            currentUser.setGender(gender);
            }

        if (userMePatchRequestDto.getPhoneNumber() != null){
            if (!userMePatchRequestDto.getPhoneNumber().equals(currentUser.getPhoneNumber())) {
                log.info("Phone change requested for user id: {}", currentUser.getId());
                var phoneVerification = phoneVerificationService.startPhoneVerification(
                        userMePatchRequestDto.getPhoneNumber(), currentUser);
                pendingVerifications.add(phoneVerification);

                log.info("Phone verification code queued for {}",
                        LogMasks.phone(userMePatchRequestDto.getPhoneNumber()));
            } else {
                log.info("Phone number is the same as the current user's number. No need to update.");
            }
        }

        if (userMePatchRequestDto.getEmail() != null) {
            if (!userMePatchRequestDto.getEmail().equals(currentUser.getEmail())) {

                if (existsByEmail(userMePatchRequestDto.getEmail())) {
                    throw new ValidationException(UserMessages.EMAIL_ALREADY_EXISTS);
                }
                log.info("Email change requested for user id: {}", currentUser.getId());
                var emailVerification = emailVerificationService.startEmailVerification(userMePatchRequestDto.getEmail(), currentUser);
                pendingVerifications.add(emailVerification);

                log.info("Email verification mail queued for {}", LogMasks.email(userMePatchRequestDto.getEmail()));

            } else {
                log.info("Email is the same as the current user's email. No need to update.");
            }
        }

            if (userMePatchRequestDto.getIban() !=null){
                log.info("IBAN updated for user id: {}", currentUser.getId());

                bankAccountService.savePrimaryAccount(currentUser, userMePatchRequestDto.getIban());
            }

            if (userMePatchRequestDto.getAbout() != null){
                log.info("Updating about to: {}, from {}", userMePatchRequestDto.getAbout(), currentUser.getAbout());
                currentUser.setAbout(userMePatchRequestDto.getAbout());
            }

            if (userMePatchRequestDto.getQualifications() != null){
                log.info("Updating qualifications to: {}, from {}", userMePatchRequestDto.getQualifications(), currentUser.getQualifications());
                currentUser.setQualifications(userMePatchRequestDto.getQualifications());
            }

            if (userMePatchRequestDto.getJobTitle() != null){
                currentUser.setJobTitle(userMePatchRequestDto.getJobTitle());
            }

            if (userMePatchRequestDto.getInterests() != null){
                log.info("Updating interests to: {}, from {}", userMePatchRequestDto.getInterests(), currentUser.getInterests());
                currentUser.setInterests(userMePatchRequestDto.getInterests());
            }

            User savedUser = userDao.save(currentUser);

            userProducerService.publishUserProfileUpdated(savedUser);

            log.info("User with ID: {} patched successfully.", savedUser.getId());

            return new UserUpdateResponseDto(toMeDto(savedUser), pendingVerifications);
    }

    @Override
    @Transactional
    public void updateCurrentUserPassword(ChangePasswordDto userMeChangePasswordPutRequestDto, UUID currentSessionId) {
        log.info("Updating current user password.");
        User currentUser = securityService.getAuthenticatedUser();
        log.info("Updating password for user with ID: {}", currentUser.getId());

        if (userMeChangePasswordPutRequestDto.getConfirmNewPassword().equals(userMeChangePasswordPutRequestDto.getCurrentPassword())){
            log.warn("New password is the same as the current password. No need to update.");
            throw new ValidationException(UserMessages.NEW_PASSWORD_CANNOT_BE_SAME_AS_CURRENT_PASSWORD);
        }

        if (!userMeChangePasswordPutRequestDto.getConfirmNewPassword().equals(userMeChangePasswordPutRequestDto.getNewPassword())){
            log.warn("New password does not match the confirmed new password. No need to update.");
            throw new ValidationException(UserMessages.PASSWORDS_DO_NOT_MATCH);
        }

        var matches = passwordEncoder.matches(userMeChangePasswordPutRequestDto.getCurrentPassword(), currentUser.getPassword());

        if (!matches){
            log.warn("Current password is incorrect.");
            throw new ValidationException(UserMessages.CURRENT_PASSWORD_INCORRECT);
        }

        String encodedNewPassword = passwordEncoder.encode(userMeChangePasswordPutRequestDto.getNewPassword());

        currentUser.setPassword(encodedNewPassword);

        userDao.save(currentUser);
        passwordResetTokenService.revokeActiveForUser(currentUser.getId());

        sessionService.invalidateActiveSessionsForUserIdExcludingSessionId(currentUser.getId(), currentSessionId);
        log.info("Password updated successfully for user with ID: {}", currentUser.getId());
    }

    @Override
    @Transactional
    public void updateProfilePicture(MultipartFile image) {
        log.info("Updating profile picture for current user.");
        var currentUser = securityService.getAuthenticatedUser();
        log.info("Uploading new profile picture for user with ID: {}", currentUser.getId());

        Image savedProfilePicture = imageService.uploadImage(image);

        currentUser.setProfilePicture(savedProfilePicture);
        userDao.save(currentUser);
        log.info("Profile picture updated successfully for user with ID: {}", currentUser.getId());
    }

    @Override
    public void updatePhoneVerification(User user, String newPhoneNumber) {
        log.info("Updating phone verification for user with ID: {}", user.getId());
        user.setPhoneNumber(newPhoneNumber);
        user.setPhoneVerified(true);
        var savedUser = userDao.save(user);
        userProducerService.publishUserProfileUpdated(savedUser);
        log.info("Phone verification updated successfully for user with ID: {}", user.getId());
    }

    @Override
    public void updateEmailVerification(User user, String newEmail) {
        log.info("Updating email verification for user with ID: {}", user.getId());
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        var savedUser = userDao.save(user);
        userProducerService.publishUserProfileUpdated(savedUser);
        log.info("Email verification updated successfully for user with ID: {}", user.getId());

    }

    @Override
    @Transactional
    public void verificateIdentityofAuthenticatedUser(VerifyIdentityDto usersMeIdentityVerificationRequestDto) {
        log.info("Verificating identity of authenticated user.");
        User currentUser = securityService.getAuthenticatedUser();
        log.info("Verificating identity for user with ID: {}", currentUser.getId());

        if (currentUser.isIdentityVerified()){
            log.warn("User with ID: {} is already identity verified.", currentUser.getId());
            throw new ValidationException(UserMessages.USER_ALREADY_IDENTITY_VERIFIED);
        }

        PassiveAuthenticationResult evidence = verifyChipEvidence(usersMeIdentityVerificationRequestDto);

        if (userDao.existsByTcIdentityNumber(usersMeIdentityVerificationRequestDto.getTcIdentityNumber())) {
            log.warn("Identity verification refused for user {}: the identity number is already claimed.",
                    currentUser.getId());
            throw new ConflictException(UserMessages.TC_IDENTITY_NUMBER_ALREADY_IN_USE);
        }

        currentUser.setIdentityVerified(true);
        currentUser.setTcDocumentNumber(usersMeIdentityVerificationRequestDto.getTcDocumentNumber());
        stampVerifiedIdentity(currentUser, usersMeIdentityVerificationRequestDto, evidence);
        if (evidence != null) {
            currentUser.setIdentityEvidenceDigest(evidence.evidenceDigest());
            currentUser.setIdentityDocumentSigner(evidence.documentSigner());
        }
        var savedUser = userDao.save(currentUser);
        badgeService.evaluate(savedUser.getId());
        activityNotificationService.identityVerified(savedUser);
        userProducerService.publishUserProfileUpdated(savedUser);
        log.info("Identity verification successful for user with ID: {}", currentUser.getId());

    }

    @Override
    @Transactional
    public String startIdentityChallenge() {
        return identityChallengeService.issue(securityService.getAuthenticatedUser());
    }

    private void verifyChipPresence(User user, VerifyIdentityDto request, byte[] dataGroup15) {
        boolean supplied = request.getChallenge() != null && !request.getChallenge().isBlank()
                && request.getActiveAuthenticationSignature() != null
                && !request.getActiveAuthenticationSignature().isBlank()
                && dataGroup15 != null && dataGroup15.length > 0;

        if (!supplied) {
            if (identityVerificationProperties.isActiveAuthenticationRequired()) {
                throw new ValidationException(IdentityMessages.ACTIVE_AUTHENTICATION_REQUIRED);
            }
            return;
        }

        byte[] signature;
        try {
            signature = java.util.Base64.getDecoder()
                    .decode(request.getActiveAuthenticationSignature().trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(IdentityMessages.CHALLENGE_INVALID);
        }

        identityChallengeService.consume(user, request.getChallenge().trim(), dataGroup15, signature);
    }

    private void stampVerifiedIdentity(User user, VerifyIdentityDto request, PassiveAuthenticationResult evidence) {
        if (evidence == null) {
            user.setFirstName(capitalizeFirstLetter(request.getFirstName()));
            user.setLastName(capitalizeFirstLetter(request.getLastName()));
            user.setBirthDate(request.getBirthDate());
            user.setTcIdentityNumber(request.getTcIdentityNumber());
            return;
        }

        user.setBirthDate(evidence.birthDate());
        user.setTcIdentityNumber(evidence.nationalIdentityNumber());

        evidence.nationalName().ifPresentOrElse(
                name -> {
                    user.setFirstName(capitalizeFirstLetter(name.givenNames()));
                    user.setLastName(capitalizeFirstLetter(name.surname()));
                    log.info("Identity fields for user {} taken from the chip, name from DG11.", user.getId());
                },
                () -> {
                    user.setFirstName(capitalizeFirstLetter(request.getFirstName()));
                    user.setLastName(capitalizeFirstLetter(request.getLastName()));
                    log.info("Identity fields for user {} taken from the chip, name kept from the request "
                            + "because DG11 was not supplied.", user.getId());
                });
    }

    private PassiveAuthenticationResult verifyChipEvidence(VerifyIdentityDto request) {
        if (!identityVerificationProperties.isEnabled()) {
            return null;
        }

        if (request.getSod() == null || request.getSod().isBlank()
                || request.getDg1() == null || request.getDg1().isBlank()) {
            throw new ValidationException(IdentityMessages.EVIDENCE_REQUIRED);
        }

        byte[] securityObject;
        byte[] dataGroup1;
        byte[] dataGroup11 = null;
        byte[] dataGroup15 = null;
        try {
            securityObject = java.util.Base64.getDecoder().decode(request.getSod().trim());
            dataGroup1 = java.util.Base64.getDecoder().decode(request.getDg1().trim());
            if (request.getDg11() != null && !request.getDg11().isBlank()) {
                dataGroup11 = java.util.Base64.getDecoder().decode(request.getDg11().trim());
            }
            if (request.getDg15() != null && !request.getDg15().isBlank()) {
                dataGroup15 = java.util.Base64.getDecoder().decode(request.getDg15().trim());
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException(IdentityMessages.SOD_UNREADABLE);
        }

        PassiveAuthenticationResult result;
        try {
            result = cscaTrustStore.verifier().verify(securityObject, dataGroup1, dataGroup11, dataGroup15);
        } catch (PassiveAuthenticationException e) {
            log.warn("Passive authentication failed: {}", e.getMessage());
            throw new ValidationException(e.getMessageKey());
        }

        if (!result.nationalIdentityNumber().equals(request.getTcIdentityNumber())) {
            log.warn("Passive authentication refused: the identity number does not match the signed chip data.");
            throw new ValidationException(IdentityMessages.IDENTITY_NUMBER_MISMATCH);
        }

        verifyChipPresence(securityService.getAuthenticatedUser(), request, dataGroup15);

        if (!result.machineReadableZone().matchesHolder(request.getFirstName(), request.getLastName())) {
            log.warn("Passive authentication refused: the name does not match the signed chip data.");
            throw new ValidationException(IdentityMessages.NAME_MISMATCH);
        }

        if (!result.birthDate().equals(request.getBirthDate())) {
            log.warn("Passive authentication refused: the birth date does not match the signed chip data.");
            throw new ValidationException(IdentityMessages.BIRTH_DATE_MISMATCH);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public UserMeResponseDto getCurrentUserFromDatabaseDto() {
        log.debug("Getting current user DTO.");
        return toMeDto(securityService.getAuthenticatedUser());
    }

    @Override
    @Transactional
    public AddressDto updateAddressForCurrentUser(AddressWriteDto addressDto) {
        log.info("Updating address for current user.");
        var currentUser = securityService.getAuthenticatedUser();
        log.info("Updating address for user with ID: {}", currentUser.getId());

        var address = addressService.createAddress(addressDto);
        log.info("Created address for user with AddressId: {}, UserId: {}", address.getId(), currentUser.getId());

        currentUser.setAddress(address);

        var savedUser = userDao.save(currentUser);
        log.info("Address updated successfully for user with ID: {}", currentUser.getId());

        userProducerService.publishUserProfileUpdated(savedUser);

        return addressMapper.toAddressDto(address);
    }

    private UserMeResponseDto toMeDto(User user) {
        String maskedIban = bankAccountService.findPrimaryIban(user.getId())
                .map(LogMasks::iban)
                .orElse(null);
        return userMapper.toUserMeResponseDto(user, maskedIban,
                badgeMapper.toBadgeDtoList(badgeService.getBadgesOf(user.getId())));
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        input = input.trim();
        return input.substring(0, 1).toUpperCase(TURKISH) + input.substring(1).toLowerCase(TURKISH);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> search(String query, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, USER_SORTABLE);
        String trimmed = query == null || query.isBlank() ? null : query.trim();
        return userDao.search(trimmed,
                pageableSanitizer.sanitize(requested, USER_SORTABLE, USER_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public User getReference(UUID id) {
        return userDao.getReferenceById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDao.RoleGrantView> getRoleGrants(UUID userId) {
        return userDao.findRoleGrants(getById(userId).getId());
    }

    @Override
    @Transactional
    public List<UserDao.RoleGrantView> grantRole(StaffPrincipal actor, UUID userId, Role role) {
        User target = getById(userId);
        rejectSystemUser(target);

        if (target.getAuthorities().contains(role)) {
            log.info("Role {} already present on user {}, nothing written.", role, target.getId());
            return userDao.findRoleGrants(target.getId());
        }

        addRole(target, role);
        if (actor == null) {
            UUID grantedById = securityService.getAuthenticatedUserId();
            userDao.stampRoleGrant(target.getId(), role.name(), Instant.now(), grantedById);
            log.info("Role {} granted to user {} by bridge admin {}", role, target.getId(), grantedById);
        } else {
            userDao.stampRoleGrantTime(target.getId(), role.name(), Instant.now());
            log.info("Role {} granted to user {} by staff {}", role, target.getId(), actor.subject());
        }

        return userDao.findRoleGrants(target.getId());
    }

    @Override
    @Transactional
    public void revokeRole(StaffPrincipal actor, UUID userId, Role role) {
        User target = getById(userId);
        rejectSystemUser(target);

        if (role == Role.ROLE_USER) {
            throw new ConflictException(AdminMessages.MEMBERSHIP_ROLE_PROTECTED);
        }

        if (!target.getAuthorities().contains(role)) {
            return;
        }

        if (role == Role.ROLE_ADMIN) {
            if (actor == null && target.getId().equals(securityService.getAuthenticatedUserId())) {
                throw new ConflictException(AdminMessages.OWN_ADMIN_ROLE_PROTECTED);
            }
            if (userDao.countUsersWithRole(Role.ROLE_ADMIN.name()) <= 1) {
                throw new ConflictException(AdminMessages.LAST_ADMIN_ROLE_PROTECTED);
            }
        }

        target.getAuthorities().remove(role);
        userDao.saveAndFlush(target);
        log.info("Role {} revoked from user {}", role, target.getId());
    }

    @Override
    @Transactional
    public boolean bootstrapAdminRole(String email) {
        User target = getByEmail(email);

        if (target.getAuthorities().contains(Role.ROLE_ADMIN)) {
            return false;
        }

        addRole(target, Role.ROLE_ADMIN);
        userDao.stampRoleGrantTime(target.getId(), Role.ROLE_ADMIN.name(), Instant.now());
        log.info("Bootstrap granted ROLE_ADMIN to user {}", target.getId());

        return true;
    }

    private void addRole(User target, Role role) {
        target.getAuthorities().add(role);
        userDao.saveAndFlush(target);
    }

    private void rejectSystemUser(User target) {
        if (SystemUser.ID.equals(target.getId())) {
            throw new ConflictException(AdminMessages.SYSTEM_USER_ROLES_PROTECTED);
        }
    }
}
