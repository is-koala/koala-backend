package com.exskylab.koala.core.utilities.identity;

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
        // Real card: I<TUR + document no (9) + check digit + "<" + TCKN (11) + "<<<"
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
        // ICAO note (j): a document number longer than 9 characters puts a filler where the
        // check digit belongs and spills the rest into the optional data field.
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
}
