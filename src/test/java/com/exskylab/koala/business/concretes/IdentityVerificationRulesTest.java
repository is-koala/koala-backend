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
import com.exskylab.koala.core.utilities.identity.AdditionalPersonalDetails;
import com.exskylab.koala.core.utilities.identity.CscaTrustStore;
import com.exskylab.koala.core.utilities.identity.DataGroupTags;
import com.exskylab.koala.core.utilities.identity.MachineReadableZone;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private com.exskylab.koala.business.abstracts.IdentityChallengeService identityChallengeService;

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
        identityChallengeService = mock(com.exskylab.koala.business.abstracts.IdentityChallengeService.class);

        manager = new UserManager(userDao, mock(EmailVerificationService.class), mock(UserMapper.class),
                mock(PasswordEncoder.class), mock(SessionService.class), mock(ImageService.class),
                security, mock(AddressService.class),
                mock(AddressMapper.class), producer, mock(BankAccountService.class),
                identityProperties, trustStore, identityChallengeService,
                mock(PhoneVerificationService.class), badgeService, mock(BadgeMapper.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer(), notifications,
                mock(com.exskylab.koala.business.abstracts.PasswordResetTokenService.class),
                mock(com.exskylab.koala.business.abstracts.RatingSummaryService.class));

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
    void theFiveFieldFormIsNotAProductPathWhilePassiveAuthIsOff() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(request()));

        assertEquals("IDENTITY_DOCUMENT_REVIEW_REQUIRED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verify(userDao, never()).save(any());
        verifyNoInteractions(trustStore);
    }

    @Test
    void theNameIsStillCapitalisedWithTheTurkishLocale() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        assertEquals("Yusuf", current.getFirstName());
        assertEquals("Acmaci", current.getLastName());
    }

    @Test
    void theDocumentNumberIsKeptAsEvidence() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        assertEquals(DOCUMENT_NO, current.getTcDocumentNumber());
    }

    @Test
    void anIdentityAlreadyClaimedByAnotherAccountIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);
        when(userDao.existsByTcIdentityNumber(TCKN)).thenReturn(true);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

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
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        verify(badgeService).evaluate(current.getId());
        verify(notifications).identityVerified(current);
        verify(producer).publishUserProfileUpdated(current);
    }

    private void enablePassiveAuthentication(PassiveAuthenticationVerifier verifier) {
        identityProperties.setEnabled(true);
        when(trustStore.verifier()).thenReturn(verifier);
    }

    private static MachineReadableZone chipZone() {
        String line1 = "I<TURA01A000004<" + TCKN + "<<<";
        String line2 = "9804121M3001017TUR<<<<<<<<<<<4";
        String line3 = "ACMACI<<YUSUF<<<<<<<<<<<<<<<<<";
        byte[] mrz = (line1 + line2 + line3).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] dg1 = new byte[mrz.length + 5];
        dg1[0] = 0x61; dg1[1] = (byte) (mrz.length + 3);
        dg1[2] = 0x5F; dg1[3] = 0x1F; dg1[4] = (byte) mrz.length;
        System.arraycopy(mrz, 0, dg1, 5, mrz.length);
        return MachineReadableZone.fromDataGroup1(dg1).orElseThrow();
    }

    private static VerifyIdentityDto requestWithChip() {
        VerifyIdentityDto dto = request();
        dto.setSod("AAECAw==");
        dto.setDg1("BAUGBw==");
        return dto;
    }

    @Test
    void withThePassiveGateOffChipEvidenceDoesNotStampIdentityEither() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_DOCUMENT_REVIEW_REQUIRED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verify(userDao, never()).save(any());
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
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=Turkiye Document Signer", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        assertTrue(current.isIdentityVerified());
        assertEquals("abc123", current.getIdentityEvidenceDigest());
        assertEquals("CN=Turkiye Document Signer", current.getIdentityDocumentSigner());
    }

    @Test
    void anIdentityNumberThatDisagreesWithTheChipIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult("99999999999", LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_NUMBER_MISMATCH", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void aBirthDateThatDisagreesWithTheChipIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(TCKN, LocalDate.of(1970, 1, 1), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_BIRTH_DATE_MISMATCH", thrown.getErrorCode());
    }

    @Test
    void aCryptographicFailureIsSurfacedWithItsOwnCode() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenThrow(
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

    @Test
    void aNameThatDisagreesWithTheChipIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setLastName("kahraman");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(dto));

        assertEquals("IDENTITY_NAME_MISMATCH", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void theIdentityFieldsAreTakenFromTheChipNotFromTheRequestBody() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setTcIdentityNumber(TCKN);
        dto.setBirthDate(LocalDate.of(1998, 4, 12));

        manager.verificateIdentityofAuthenticatedUser(dto);

        assertEquals(TCKN, current.getTcIdentityNumber());
        assertEquals(LocalDate.of(1998, 4, 12), current.getBirthDate());
    }

    @Test
    void whenTheChipCarriesDataGroup11TheStoredNameComesFromIt() {
        AdditionalPersonalDetails fromChip =
                AdditionalPersonalDetails.parse(dataGroup11("ÇAĞRI", "İLKER")).orElseThrow();

        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), fromChip, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setFirstName("yusuf");
        dto.setLastName("acmaci");

        manager.verificateIdentityofAuthenticatedUser(dto);

        assertEquals("İlker", current.getFirstName());
        assertEquals("Çağrı", current.getLastName());
    }

    @Test
    void withoutDataGroup11TheSubmittedNameIsKept() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        manager.verificateIdentityofAuthenticatedUser(requestWithChip());

        assertEquals("Yusuf", current.getFirstName());
        assertEquals("Acmaci", current.getLastName());
    }

    private static byte[] dataGroup11(String surname, String givenNames) {
        byte[] name = (surname + "<<" + givenNames).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] inner = new byte[3 + name.length];
        inner[0] = 0x5F; inner[1] = 0x0E; inner[2] = (byte) name.length;
        System.arraycopy(name, 0, inner, 3, name.length);
        byte[] outer = new byte[2 + inner.length];
        outer[0] = (byte) DataGroupTags.DG11; outer[1] = (byte) inner.length;
        System.arraycopy(inner, 0, outer, 2, inner.length);
        return outer;
    }

    @Test
    void withoutTheActiveGateAChipReadWithoutASignatureIsAccepted() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        assertDoesNotThrow(() -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));
        verifyNoInteractions(identityChallengeService);
    }

    @Test
    void withTheActiveGateOnAChipReadWithoutASignatureIsRefused() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);
        identityProperties.setActiveAuthenticationRequired(true);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(requestWithChip()));

        assertEquals("IDENTITY_ACTIVE_AUTHENTICATION_REQUIRED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void aSuppliedSignatureIsCheckedAgainstTheIssuedChallenge() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        manager.verificateIdentityofAuthenticatedUser(dto);

        verify(identityChallengeService).consume(eq(current), eq("BBBBBBBBBBA="), any(), any(), any());
    }

    @Test
    void theSuppliedChipKeyIsHandedToPassiveAuthenticationBeforeItIsTrusted() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        manager.verificateIdentityofAuthenticatedUser(dto);

        verify(verifier).verify(any(), any(), any(), any(),
                org.mockito.AdditionalMatchers.aryEq(new byte[]{0, 1, 2, 3}));
    }

    @Test
    void aChipKeyTheSecurityObjectNeverSignedNeverReachesTheChallenge() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenThrow(
                new PassiveAuthenticationException("{identity.chip.key.unsigned}", "dg15 not in sod"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(dto));

        assertEquals("IDENTITY_CHIP_KEY_UNSIGNED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verifyNoInteractions(identityChallengeService);
    }

    @Test
    void aSuppliedDataGroup14IsHandedToPassiveAuthenticationAndToTheChallenge() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg14("bgIBAQ==");
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        manager.verificateIdentityofAuthenticatedUser(dto);

        verify(verifier).verify(any(), any(), any(),
                org.mockito.AdditionalMatchers.aryEq(new byte[]{0x6E, 2, 1, 1}),
                org.mockito.AdditionalMatchers.aryEq(new byte[]{0, 1, 2, 3}));
        verify(identityChallengeService).consume(eq(current), eq("BBBBBBBBBBA="),
                org.mockito.AdditionalMatchers.aryEq(new byte[]{0, 1, 2, 3}),
                org.mockito.AdditionalMatchers.aryEq(new byte[]{0x6E, 2, 1, 1}),
                any());
    }

    @Test
    void withoutDataGroup14TheVerifierAndTheChallengeReceiveNull() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenReturn(new PassiveAuthenticationResult(
                TCKN, LocalDate.of(1998, 4, 12), chipZone(), null, "CN=DS", "abc123"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        manager.verificateIdentityofAuthenticatedUser(dto);

        verify(verifier).verify(any(), any(), any(), isNull(), any());
        verify(identityChallengeService).consume(eq(current), eq("BBBBBBBBBBA="), any(), isNull(), any());
    }

    @Test
    void aDataGroup14TheSecurityObjectNeverSignedNeverReachesTheChallenge() {
        PassiveAuthenticationVerifier verifier = mock(PassiveAuthenticationVerifier.class);
        when(verifier.verify(any(), any(), any(), any(), any())).thenThrow(
                new PassiveAuthenticationException("{identity.security.info.unsigned}", "dg14 not in sod"));
        enablePassiveAuthentication(verifier);

        VerifyIdentityDto dto = requestWithChip();
        dto.setDg14("bgIBAQ==");
        dto.setDg15("AAECAw==");
        dto.setChallenge("BBBBBBBBBBA=");
        dto.setActiveAuthenticationSignature("CQoLDA==");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.verificateIdentityofAuthenticatedUser(dto));

        assertEquals("IDENTITY_SECURITY_INFO_UNSIGNED", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verifyNoInteractions(identityChallengeService);
    }
}
