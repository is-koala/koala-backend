package com.exskylab.koala.business.concretes;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.address.request.CreateAddressRequestDto;
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
import com.exskylab.koala.core.utilities.kps.KimlikPaylasimSistemiService;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
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
    private final KimlikPaylasimSistemiService kimlikPaylasimSistemiService;
    private final AddressMapper addressMapper;
    private final UserProducerService userProducerService;
    private final BankAccountService bankAccountService;
    private final PhoneVerificationService phoneVerificationService;
    private final BadgeService badgeService;
    private final BadgeMapper badgeMapper;
    private final ActivityNotificationService activityNotificationService;

    public UserManager(UserDao userDao,
                       EmailVerificationService emailVerificationService,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       SessionService sessionService,
                       ImageService imageService,
                       SecurityService securityService,
                       AddressService addressService,
                       KimlikPaylasimSistemiService kimlikPaylasimSistemiService,
                       AddressMapper addressMapper, UserProducerService userProducerService,
                       BankAccountService bankAccountService,
                       PhoneVerificationService phoneVerificationService,
                       BadgeService badgeService, BadgeMapper badgeMapper,
                                PageableSanitizer pageableSanitizer,
                       ActivityNotificationService activityNotificationService) {
        this.userDao = userDao;
        this.emailVerificationService = emailVerificationService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.imageService = imageService;
        this.securityService = securityService;
        this.addressService = addressService;
        this.kimlikPaylasimSistemiService = kimlikPaylasimSistemiService;
        this.addressMapper = addressMapper;
        this.userProducerService = userProducerService;
        this.bankAccountService = bankAccountService;
        this.phoneVerificationService = phoneVerificationService;
        this.badgeService = badgeService;
        this.badgeMapper = badgeMapper;
            this.pageableSanitizer = pageableSanitizer;
                this.activityNotificationService = activityNotificationService;
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

        boolean isVerified = kimlikPaylasimSistemiService.kimlikDogrula(
                usersMeIdentityVerificationRequestDto.getTcIdentityNumber(),
                usersMeIdentityVerificationRequestDto.getFirstName(),
                usersMeIdentityVerificationRequestDto.getLastName(),
                usersMeIdentityVerificationRequestDto.getBirthDate(),
                usersMeIdentityVerificationRequestDto.getTcDocumentNumber()
        );

        if (!isVerified){
            log.warn("Identity verification failed for user with ID: {}", currentUser.getId());
            throw new ValidationException(UserMessages.IDENTITY_VERIFICATION_FAILED);
        }

        currentUser.setIdentityVerified(true);
        currentUser.setFirstName(capitalizeFirstLetter(usersMeIdentityVerificationRequestDto.getFirstName()));
        currentUser.setLastName(capitalizeFirstLetter(usersMeIdentityVerificationRequestDto.getLastName()));
        currentUser.setBirthDate(usersMeIdentityVerificationRequestDto.getBirthDate());
        currentUser.setTcIdentityNumber(usersMeIdentityVerificationRequestDto.getTcIdentityNumber());
        var savedUser = userDao.save(currentUser);
        badgeService.evaluate(savedUser.getId());
        activityNotificationService.identityVerified(savedUser);
        userProducerService.publishUserProfileUpdated(savedUser);
        log.info("Identity verification successful for user with ID: {}", currentUser.getId());

    }

    @Override
    @Transactional(readOnly = true)
    public UserMeResponseDto getCurrentUserFromDatabaseDto() {
        log.debug("Getting current user DTO.");
        return toMeDto(securityService.getAuthenticatedUser());
    }

    @Override
    @Transactional
    public AddressDto updateAddressForCurrentUser(CreateAddressRequestDto addressDto) {
        log.info("Updating address for current user.");
        var currentUser = securityService.getAuthenticatedUser();
        log.info("Updating address for user with ID: {}", currentUser.getId());

        var address = addressService.createAddress(addressDto);
        log.info("Created address for user with AddressId: {}, UserId: ", address.getId(), currentUser.getId());

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
}
