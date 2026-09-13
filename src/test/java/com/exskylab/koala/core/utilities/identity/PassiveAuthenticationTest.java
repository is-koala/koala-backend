package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PassiveAuthenticationTest {

    private static final String TCKN = "10000000146";

    private static TestChip turkey;
    private static TestChip otherCountry;
    private static byte[] dg1;

    @BeforeAll
    static void issueChips() throws Exception {
        turkey = TestChip.issue("Turkiye");
        otherCountry = TestChip.issue("Elsewhere");
        dg1 = TestChip.dataGroup1(TCKN, "980412");
    }

    private PassiveAuthenticationVerifier verifierTrusting(TestChip chip) {
        return new PassiveAuthenticationVerifier(chip.trustAnchors());
    }

    @Test
    void aGenuineChipReadPassesAndYieldsTheSignedIdentity() throws Exception {
        PassiveAuthenticationResult result =
                verifierTrusting(turkey).verify(turkey.securityObject(dg1), dg1);

        assertEquals(TCKN, result.nationalIdentityNumber());
        assertEquals(LocalDate.of(1998, 4, 12), result.birthDate());
        assertTrue(result.documentSigner().contains("Document Signer"));
        assertNotNull(result.evidenceDigest());
    }

    @Test
    void theEvidenceDigestIsStableForTheSameSecurityObject() throws Exception {
        byte[] sod = turkey.securityObject(dg1);

        assertEquals(verifierTrusting(turkey).verify(sod, dg1).evidenceDigest(),
                verifierTrusting(turkey).verify(sod, dg1).evidenceDigest());
    }

    @Test
    void aTamperedDataGroupIsRefused() throws Exception {
        byte[] sod = turkey.securityObject(dg1);
        byte[] tampered = TestChip.dataGroup1("99999999999", "980412");

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, tampered));

        assertEquals("{identity.data.group.tampered}", thrown.getMessageKey());
    }

    @Test
    void aChipSignedByAnUntrustedCountryIsRefused() throws Exception {
        byte[] sod = otherCountry.securityObject(dg1);

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1));

        assertEquals("{identity.untrusted.document.signer}", thrown.getMessageKey());
    }

    @Test
    void aForgedSignatureIsRefused() throws Exception {
        byte[] sod = turkey.securityObject(dg1);
        sod[sod.length - 20] ^= 0x40;

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1));

        assertTrue(Set.of("{identity.signature.invalid}", "{identity.sod.unreadable}")
                .contains(thrown.getMessageKey()), thrown.getMessageKey());
    }

    @Test
    void garbageInsteadOfASecurityObjectIsRefused() {
        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify("bu bir sod degil".getBytes(), dg1));

        assertEquals("{identity.sod.unreadable}", thrown.getMessageKey());
    }

    @Test
    void withoutTrustAnchorsNothingVerifies() throws Exception {
        byte[] sod = turkey.securityObject(dg1);

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> new PassiveAuthenticationVerifier(Set.of()).verify(sod, dg1));

        assertEquals("{identity.no.trust.anchors}", thrown.getMessageKey());
    }

    @Test
    void theMachineReadableZoneIsParsedFromTheTaggedDataGroup() {
        MachineReadableZone mrz = MachineReadableZone.fromDataGroup1(dg1).orElseThrow();

        assertEquals(TCKN, mrz.nationalIdentityNumber().orElseThrow());
        assertEquals(LocalDate.of(1998, 4, 12), mrz.birthDate().orElseThrow());
    }

    @Test
    void aDataGroupThatIsNotATd1ZoneIsRejected() {
        assertTrue(MachineReadableZone.fromDataGroup1("kisa".getBytes()).isEmpty());
    }

    @Test
    void theIdentityNumberIsReadFromOptionalDataAfterTheSeparator() {
        byte[] realLayout = TestChip.dataGroup1WithUpperLine("I<TURA01A000004<" + TCKN + "<<<", "980412");

        assertEquals(TCKN, MachineReadableZone.fromDataGroup1(realLayout).orElseThrow()
                .nationalIdentityNumber().orElseThrow());
    }

    @Test
    void anIdentityNumberWithoutTheSeparatorIsAlsoRead() {
        byte[] noSeparator = TestChip.dataGroup1WithUpperLine("I<TURA01A000004" + TCKN + "<<<<", "980412");

        assertEquals(TCKN, MachineReadableZone.fromDataGroup1(noSeparator).orElseThrow()
                .nationalIdentityNumber().orElseThrow());
    }

    @Test
    void aTruncatedDocumentNumberIsNeverMistakenForAnIdentityNumber() {
        byte[] noteJ = TestChip.dataGroup1WithUpperLine("I<TURD23145890<12331234567<<<<", "980412");

        assertTrue(MachineReadableZone.fromDataGroup1(noteJ).orElseThrow()
                .nationalIdentityNumber().isEmpty());
    }

    @Test
    void optionalDataThatIsNotExactlyElevenDigitsIsRefused() {
        byte[] shortNumber = TestChip.dataGroup1WithUpperLine("I<TURA01A000004<1234567890<<<<", "980412");

        assertTrue(MachineReadableZone.fromDataGroup1(shortNumber).orElseThrow()
                .nationalIdentityNumber().isEmpty());
    }

    @Test
    void theHolderNameOnTheCardMustMatchTheClaim() {
        MachineReadableZone mrz = MachineReadableZone.fromDataGroup1(dg1).orElseThrow();

        assertTrue(mrz.matchesHolder("Yusuf", "Acmaci"));
        assertFalse(mrz.matchesHolder("Mehmet", "Acmaci"));
        assertFalse(mrz.matchesHolder("Yusuf", "Kahraman"));
    }

    @Test
    void turkishCharactersInTheClaimedNameAreFoldedBeforeComparing() {
        byte[] card = TestChip.dataGroup1WithNameLine("CAGRI<<ILKER<<<<<<<<<<<<<<<<<<");

        MachineReadableZone mrz = MachineReadableZone.fromDataGroup1(card).orElseThrow();

        assertTrue(mrz.matchesHolder("İlker", "Çağrı"));
    }

    @Test
    void aNameTruncatedByTheThirtyCharacterLimitStillMatches() {
        byte[] card = TestChip.dataGroup1WithNameLine("KARAHANOGULLARINDA<<ABDULLAHM<");

        MachineReadableZone mrz = MachineReadableZone.fromDataGroup1(card).orElseThrow();

        assertTrue(mrz.matchesHolder("Abdullahmehmet", "Karahanogullarindan"),
                "MRZ 30 karakterde kesiyor, uzun adli kullanici kilitlenmemeli");
    }

    @Test
    void theNationalSpellingOfTheNameComesFromDataGroup11() throws Exception {
        byte[] dg11 = TestChip.dataGroup11("ÇAĞRI", "İLKER");
        byte[] sod = turkey.securityObjectWithDg11(dg1, dg11);

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, dg11);

        AdditionalPersonalDetails name = result.nationalName().orElseThrow();
        assertEquals("ÇAĞRI", name.surname());
        assertEquals("İLKER", name.givenNames());
    }

    @Test
    void aTamperedDataGroup11IsRefused() throws Exception {
        byte[] dg11 = TestChip.dataGroup11("ACMACI", "YUSUF");
        byte[] sod = turkey.securityObjectWithDg11(dg1, dg11);
        byte[] forged = TestChip.dataGroup11("KAHRAMAN", "YAKUP");

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1, forged));

        assertEquals("{identity.data.group.tampered}", thrown.getMessageKey());
    }

    @Test
    void withoutDataGroup11TheChipReadStillVerifies() throws Exception {
        PassiveAuthenticationResult result =
                verifierTrusting(turkey).verify(turkey.securityObject(dg1), dg1, null);

        assertEquals(TCKN, result.nationalIdentityNumber());
        assertTrue(result.nationalName().isEmpty());
    }

    @Test
    void aDataGroup11WithoutAFullNameFieldIsIgnoredRatherThanFatal() throws Exception {
        byte[] dg11 = new byte[]{(byte) DataGroupTags.DG11, 0x03, 0x5C, 0x01, 0x0E};
        byte[] sod = turkey.securityObjectWithDg11(dg1, dg11);

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, dg11);

        assertTrue(result.nationalName().isEmpty());
    }

    @Test
    void theChipsOwnDataGroup15VerifiesAgainstTheSignedSecurityObject() throws Exception {
        byte[] dg15 = TestChip.dataGroup15();
        byte[] sod = turkey.securityObjectWithDg15(dg1, dg15);

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, null, dg15);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }

    @Test
    void aDataGroup15FromTheAttackersOwnKeyPairIsRefused() throws Exception {
        byte[] chipKey = TestChip.dataGroup15();
        byte[] sod = turkey.securityObjectWithDg15(dg1, chipKey);
        byte[] attackerKey = TestChip.dataGroup15();

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1, null, attackerKey),
                "calinan sod+dg1 ile saldirganin kendi anahtari Active Authentication'i gecmemeli");

        assertEquals("{identity.data.group.tampered}", thrown.getMessageKey());
    }

    @Test
    void aDataGroup15ThatTheSecurityObjectNeverSignedIsRefused() throws Exception {
        byte[] sod = turkey.securityObject(dg1);
        byte[] attackerKey = TestChip.dataGroup15();

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1, null, attackerKey),
                "SOD DG15 listelemiyorsa gonderilen anahtar uydurmadir");

        assertEquals("{identity.chip.key.unsigned}", thrown.getMessageKey());
    }

    @Test
    void aCardWithoutDataGroup15StillVerifiesWhenNoKeyIsSent() throws Exception {
        PassiveAuthenticationResult result =
                verifierTrusting(turkey).verify(turkey.securityObject(dg1), dg1, null, null);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }

    @Test
    void aCardThatCarriesDataGroup15StillVerifiesWhenNoKeyIsSent() throws Exception {
        byte[] sod = turkey.securityObjectWithDg15(dg1, TestChip.dataGroup15());

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, null, new byte[0]);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }

    @Test
    void theChipsOwnDataGroup14VerifiesAgainstTheSignedSecurityObject() throws Exception {
        byte[] dg14 = TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_SHA256);
        byte[] dg15 = TestChip.dataGroup15();
        byte[] sod = turkey.securityObjectForHashes(TestChip.sha256(dg1), null,
                TestChip.sha256(dg14), TestChip.sha256(dg15));

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, null, dg14, dg15);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }

    @Test
    void aTamperedDataGroup14IsRefused() throws Exception {
        byte[] dg14 = TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_SHA256);
        byte[] sod = turkey.securityObjectWithDg14(dg1, dg14);
        byte[] swapped = TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_SHA1);

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1, null, swapped, null),
                "algoritma bilgisi SOD'daki DG14 ile uyusmuyorsa kurcalanmistir");

        assertEquals("{identity.data.group.tampered}", thrown.getMessageKey());
    }

    @Test
    void aDataGroup14ThatTheSecurityObjectNeverSignedIsRefused() throws Exception {
        byte[] sod = turkey.securityObject(dg1);
        byte[] dg14 = TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_SHA256);

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> verifierTrusting(turkey).verify(sod, dg1, null, dg14, null),
                "SOD DG14 listelemiyorsa gonderilen guvenlik bilgisi uydurmadir");

        assertEquals("{identity.security.info.unsigned}", thrown.getMessageKey());
    }

    @Test
    void aCardThatCarriesDataGroup14StillVerifiesWhenItIsNotSent() throws Exception {
        byte[] sod = turkey.securityObjectWithDg14(dg1, TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_SHA256));

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(sod, dg1, null, null, null);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }

    @Test
    void aSecurityObjectWithALongFormApplicationTagLengthIsStillParsed() throws Exception {
        byte[] sod = turkey.securityObject(dg1);
        byte[] wrapped = new byte[sod.length + 5];
        wrapped[0] = (byte) DataGroupTags.SOD;
        wrapped[1] = (byte) 0x83;
        wrapped[2] = (byte) (sod.length >> 16);
        wrapped[3] = (byte) (sod.length >> 8);
        wrapped[4] = (byte) sod.length;
        System.arraycopy(sod, 0, wrapped, 5, sod.length);

        PassiveAuthenticationResult result = verifierTrusting(turkey).verify(wrapped, dg1);

        assertEquals(TCKN, result.nationalIdentityNumber());
    }
}
