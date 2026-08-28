package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.user.request.UpdateProfileDto;
import com.exskylab.koala.core.mappers.AddressMapper;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.utilities.kps.KimlikPaylasimSistemiService;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserPatchRulesTest {

    private UserDao userDao;
    private PhoneVerificationService phoneVerificationService;
    private BankAccountService bankAccountService;
    private UserManager userManager;

    private User me;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        phoneVerificationService = mock(PhoneVerificationService.class);
        bankAccountService = mock(BankAccountService.class);
        SecurityService securityService = mock(SecurityService.class);
        UserMapper userMapper = mock(UserMapper.class);

        me = new User();
        me.setId(UUID.randomUUID());
        me.setPhoneNumber("+905551112233");
        me.setEmail("me@koala.test");

        userManager = new UserManager(userDao, mock(EmailVerificationService.class), userMapper,
                mock(PasswordEncoder.class), mock(SessionService.class), mock(ImageService.class),
                securityService, mock(AddressService.class), mock(KimlikPaylasimSistemiService.class),
                mock(AddressMapper.class), mock(UserProducerService.class), bankAccountService,
                phoneVerificationService, mock(BadgeService.class), mock(BadgeMapper.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer());

        when(securityService.getAuthenticatedUser()).thenReturn(me);
        when(userDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountService.findPrimaryIban(any())).thenReturn(Optional.empty());
        when(phoneVerificationService.startPhoneVerification(any(), any())).thenAnswer(i -> {
            PhoneVerification verification = new PhoneVerification();
            verification.setId(UUID.randomUUID());
            verification.setNewPhoneNumber(i.getArgument(0));
            return verification;
        });
    }

    private UpdateProfileDto patch() {
        return new UpdateProfileDto();
    }

    @Test
    void aNewPhoneNumberStartsAVerificationInsteadOfBeingSwallowed() {
        UpdateProfileDto request = patch();
        request.setPhoneNumber("+905559998877");

        var response = userManager.patchCurrentUser(request);

        verify(phoneVerificationService).startPhoneVerification(eq("+905559998877"), eq(me));
        assertEquals(1, response.getPendingVerifications().size());
        assertEquals("PHONE", response.getPendingVerifications().getFirst().getType());
    }

    @Test
    void theNumberIsNotAppliedUntilTheCodeIsConfirmed() {
        UpdateProfileDto request = patch();
        request.setPhoneNumber("+905559998877");

        userManager.patchCurrentUser(request);

        assertEquals("+905551112233", me.getPhoneNumber());
        assertFalse(me.isPhoneVerified());
    }

    @Test
    void resendingTheSameNumberStartsNothing() {
        UpdateProfileDto request = patch();
        request.setPhoneNumber("+905551112233");

        var response = userManager.patchCurrentUser(request);

        verify(phoneVerificationService, never()).startPhoneVerification(any(), any());
        assertTrue(response.getPendingVerifications().isEmpty());
    }

    @Test
    void aJobTitleIsStoredOnTheProfile() {
        UpdateProfileDto request = patch();
        request.setJobTitle("Barista");

        userManager.patchCurrentUser(request);

        assertEquals("Barista", me.getJobTitle());
    }
}
