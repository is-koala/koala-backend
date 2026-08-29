package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.mappers.AddressMapper;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityVerificationRulesTest {

    private static final String TCKN = "10000000146";
    private static final String DOCUMENT_NO = "A01A00000";

    private UserDao userDao;
    private BadgeService badgeService;
    private ActivityNotificationService notifications;
    private UserProducerService producer;
    private UserManager manager;

    private User current;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        badgeService = mock(BadgeService.class);
        notifications = mock(ActivityNotificationService.class);
        producer = mock(UserProducerService.class);

        current = new User();
        current.setId(UUID.randomUUID());

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUser()).thenReturn(current);

        manager = new UserManager(userDao, mock(EmailVerificationService.class), mock(UserMapper.class),
                mock(PasswordEncoder.class), mock(SessionService.class), mock(ImageService.class),
                security, mock(AddressService.class),
                mock(AddressMapper.class), producer, mock(BankAccountService.class),
                mock(PhoneVerificationService.class), badgeService, mock(BadgeMapper.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer(), notifications);

        when(userDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static VerifyIdentityDto request() {
        VerifyIdentityDto dto = new VerifyIdentityDto();
        dto.setFirstName("yusuf");
        dto.setLastName("acmaci");
        dto.setBirthDate(LocalDate.of(1998, 4, 12));
        dto.setTcIdentityNumber(TCKN);
        dto.setTcDocumentNumber(DOCUMENT_NO);
        return dto;
    }

    @Test
    void aVerificationNoLongerDependsOnAnyExternalService() {
        assertDoesNotThrow(() -> manager.verificateIdentityofAuthenticatedUser(request()));

        assertTrue(current.isIdentityVerified());
        assertEquals(TCKN, current.getTcIdentityNumber());
        assertEquals(LocalDate.of(1998, 4, 12), current.getBirthDate());
    }

    @Test
    void theNameIsStillCapitalisedWithTheTurkishLocale() {
        manager.verificateIdentityofAuthenticatedUser(request());

        assertEquals("Yusuf", current.getFirstName());
        assertEquals("Acmaci", current.getLastName());
    }

    @Test
    void theDocumentNumberIsKeptAsEvidence() {
        manager.verificateIdentityofAuthenticatedUser(request());

        assertEquals(DOCUMENT_NO, current.getTcDocumentNumber());
    }

    @Test
    void anIdentityAlreadyClaimedByAnotherAccountIsRefused() {
        when(userDao.existsByTcIdentityNumber(TCKN)).thenReturn(true);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(request()));

        assertEquals("TC_IDENTITY_NUMBER_ALREADY_IN_USE", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verify(userDao, never()).save(any());
    }

    @Test
    void verifyingTwiceIsStillRefused() {
        current.setIdentityVerified(true);

        assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(request()));
    }

    @Test
    void theDownstreamEffectsStillFire() {
        manager.verificateIdentityofAuthenticatedUser(request());

        verify(badgeService).evaluate(current.getId());
        verify(notifications).identityVerified(current);
        verify(producer).publishUserProfileUpdated(current);
    }
}
