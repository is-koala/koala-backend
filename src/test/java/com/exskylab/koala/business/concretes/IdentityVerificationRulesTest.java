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
import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import com.exskylab.koala.core.utilities.identity.CscaTrustStore;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationException;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationResult;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationVerifier;
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
    private IdentityVerificationProperties identityProperties;
    private CscaTrustStore trustStore;

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

        identityProperties = new IdentityVerificationProperties();
        trustStore = mock(CscaTrustStore.class);

        manager = new UserManager(userDao, mock(EmailVerificationService.class), mock(UserMapper.class),
                mock(PasswordEncoder.class), mock(SessionService.class), mock(ImageService.class),
                security, mock(AddressService.class),
                mock(AddressMapper.class), producer, mock(BankAccountService.class),
                identityProperties, trustStore,
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

    private void enablePassiveAuthentication(PassiveAuthenticationVerifier verifier) {
        identityProperties.setEnabled(true);
        when(trustStore.verifier()).thenReturn(verifier);
    }

    private static VerifyIdentityDto requestWithChip() {
        VerifyIdentityDto dto = request();
        dto.setSod("AAECAw==");
        dto.setDg1("BAUGBw==");
        return dto;
    }

    @Test
    void withThePassiveGateOffTheChipEvidenceIsNotRequired() {
        assertDoesNotThrow(() -> manager.verificateIdentityofAuthenticatedUser(request()));

        assertTrue(current.isIdentityVerified());
        verifyNoInteractions(trustStore);
    }

    @Test
    void withThePassiveGateOnTheChipEvidenceBecomesMandatory() {
        identityProperties.setEnabled(true);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(request()));

        assertEquals("IDENTITY_EVIDENCE_REQUIRED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void aVerifiedChipStampsTheEvidenceOnTheAccount() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), "CN=Turkiye Document Signer", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        assertTrue(current.isIdentityVerified());
        assertEquals("abc123", current.getIdentityEvidenceDigest());
        assertEquals("CN=Turkiye Document Signer", current.getIdentityDocumentSigner());
    }

    @Test
    void anIdentityNumberThatDisagreesWithTheChipIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new PassiveAuthenticationResult(
                "99999999999", LocalDate.of(1998, 4, 12), "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_NUMBER_MISMATCH", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void aBirthDateThatDisagreesWithTheChipIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1970, 1, 1), "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_BIRTH_DATE_MISMATCH", thrown.getErrorCode());
    }

    @Test
    void aCryptographicFailureIsSurfacedWithItsOwnCode() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any())).thenThrow(
                new PassiveAuthenticationException("{identity.untrusted.document.signer}", "unknown csca"));
        enablePassiveAuthentication(verifier);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_UNTRUSTED_DOCUMENT_SIGNER", thrown.getErrorCode());
        verify(userDao, never()).save(any());
    }

    @Test
    void evidenceThatIsNotBase64IsRefusedBeforeAnyCryptography() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        enablePassiveAuthentication(verifier);
        VerifyIdentityDto dto = request();
        dto.setSod("bu base64 degil!!");
        dto.setDg1("bu da degil!!");

        assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(dto));

        verifyNoInteractions(verifier);
    }
}
