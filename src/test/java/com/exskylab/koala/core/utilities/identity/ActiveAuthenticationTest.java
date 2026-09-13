package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X962Parameters;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.signers.ISO9796d2Signer;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ActiveAuthenticationTest {

    private static final byte[] CHALLENGE = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    private static AsymmetricCipherKeyPair rsaChipKeys;
    private static byte[] rsaDg15;

    private static KeyPair ecChipKeys;
    private static byte[] ecDg15;

    @BeforeAll
    static void issueChipKeys() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        RSAKeyPairGenerator rsa = new RSAKeyPairGenerator();
        rsa.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), 2048, 80));
        rsaChipKeys = rsa.generateKeyPair();
        rsaDg15 = wrap(SubjectPublicKeyInfoFactory
                .createSubjectPublicKeyInfo(rsaChipKeys.getPublic()).getEncoded("DER"));

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        ecChipKeys = ec.generateKeyPair();
        ecDg15 = wrap(ecChipKeys.getPublic().getEncoded());
    }

    private static byte[] wrap(byte[] key) {
        byte[] wrapped = new byte[key.length + 4];
        wrapped[0] = 0x6F;
        wrapped[1] = (byte) 0x82;
        wrapped[2] = (byte) (key.length >> 8);
        wrapped[3] = (byte) (key.length & 0xFF);
        System.arraycopy(key, 0, wrapped, 4, key.length);
        return wrapped;
    }

    private static byte[] chipSignsRsa(byte[] challenge) throws Exception {
        Signature signature = Signature.getInstance("SHA1withRSA/ISO9796-2",
                BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(rsaPrivateKey());
        signature.update(challenge);
        return signature.sign();
    }

    private static java.security.PrivateKey rsaPrivateKey() throws Exception {
        var info = org.bouncycastle.crypto.util.PrivateKeyInfoFactory
                .createPrivateKeyInfo(rsaChipKeys.getPrivate());
        return java.security.KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(info.getEncoded("DER")));
    }

    private static byte[] chipSignsEc(byte[] challenge) throws Exception {
        Signature signature = Signature.getInstance("SHA256withPLAIN-ECDSA",
                BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(ecChipKeys.getPrivate());
        signature.update(challenge);
        return signature.sign();
    }

    @Test
    void anRsaChipProvesItSignedTheServerChallenge() throws Exception {
        assertDoesNotThrow(() -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, chipSignsRsa(CHALLENGE)));
    }

    @Test
    void anEllipticCurveChipProvesItSignedTheServerChallenge() throws Exception {
        assertDoesNotThrow(() -> ActiveAuthentication.verify(ecDg15, CHALLENGE, chipSignsEc(CHALLENGE)));
    }

    @Test
    void aSignatureOverAnotherChallengeIsRefused() throws Exception {
        byte[] signature = chipSignsRsa(new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, signature));

        assertEquals("{identity.chip.not.present}", thrown.getMessageKey());
    }

    @Test
    void aReplayedSignatureDoesNotVerifyAgainstAFreshChallenge() throws Exception {
        byte[] captured = chipSignsEc(CHALLENGE);
        byte[] freshChallenge = new byte[]{8, 7, 6, 5, 4, 3, 2, 1};

        assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(ecDg15, freshChallenge, captured));
    }

    @Test
    void aForgedSignatureIsRefused() throws Exception {
        byte[] signature = chipSignsRsa(CHALLENGE);
        signature[signature.length - 5] ^= 0x20;

        assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, signature));
    }

    @Test
    void aDataGroup15WithoutAKeyIsRefused() {
        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify("bu anahtar degil".getBytes(), CHALLENGE, new byte[64]));

        assertEquals("{identity.chip.key.unreadable}", thrown.getMessageKey());
    }

    @Test
    void aKeyFromAnotherChipDoesNotVerify() throws Exception {
        byte[] signature = chipSignsEc(CHALLENGE);

        assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, signature));
    }

    private static KeyPair ecKeys(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static byte[] sign(KeyPair keys, String algorithm, byte[] challenge) throws Exception {
        Signature signature = Signature.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(keys.getPrivate());
        signature.update(challenge);
        return signature.sign();
    }

    private static byte[] explicitParameterDg15(KeyPair keys, String curve) throws Exception {
        X9ECParameters parameters = ECNamedCurveTable.getByName(curve);
        byte[] point = ((org.bouncycastle.jce.interfaces.ECPublicKey) keys.getPublic()).getQ().getEncoded(false);
        SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(
                new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, new X962Parameters(parameters)), point);
        return wrap(info.getEncoded("DER"));
    }

    private static byte[] wrapWithLengthForm(byte[] key, int lengthOctets) {
        byte[] wrapped = new byte[key.length + 2 + lengthOctets];
        wrapped[0] = 0x6F;
        if (lengthOctets == 0) {
            wrapped[1] = (byte) key.length;
        } else {
            wrapped[1] = (byte) (0x80 | lengthOctets);
            for (int i = 0; i < lengthOctets; i++) {
                wrapped[2 + i] = (byte) (key.length >> (8 * (lengthOctets - 1 - i)));
            }
        }
        System.arraycopy(key, 0, wrapped, 2 + lengthOctets, key.length);
        return wrapped;
    }

    @Test
    void aBrainpoolChipSigningWithSha256PlainProvesItself() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));
    }

    @Test
    void aSecp384ChipSigningWithSha384PlainProvesItself() throws Exception {
        KeyPair keys = ecKeys("secp384r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE,
                sign(keys, "SHA384withPLAIN-ECDSA", CHALLENGE)));
    }

    @Test
    void aSecp384ChipSigningWithSha384DerProvesItself() throws Exception {
        KeyPair keys = ecKeys("secp384r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE,
                sign(keys, "SHA384withECDSA", CHALLENGE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SHA1withPLAIN-ECDSA", "SHA224withPLAIN-ECDSA", "SHA256withPLAIN-ECDSA",
            "SHA384withPLAIN-ECDSA", "SHA512withPLAIN-ECDSA",
            "SHA1withECDSA", "SHA224withECDSA", "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA"})
    void everyEcdsaHashAndEncodingInTheSpectrumVerifiesWithoutDataGroup14(String algorithm) throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE, sign(keys, algorithm, CHALLENGE)));
    }

    @Test
    void aSecp384SignatureOverAnotherChallengeIsRefused() throws Exception {
        KeyPair keys = ecKeys("secp384r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] signature = sign(keys, "SHA384withPLAIN-ECDSA", new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, CHALLENGE, signature));

        assertEquals("{identity.chip.not.present}", thrown.getMessageKey());
    }

    @Test
    void aChipKeyWithExplicitCurveParametersIsReadAndVerified() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = explicitParameterDg15(keys, "brainpoolP256r1");

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, CHALLENGE, sign(keys, "SHA256withPLAIN-ECDSA",
                        new byte[]{9, 9, 9, 9, 9, 9, 9, 9})));

        assertTrue(thrown.getMessage().contains("curve=explicit(brainpoolP256r1)"), thrown.getMessage());
    }

    @Test
    void theAlgorithmDeclaredInDataGroup14IsHonouredEvenOutsideTheDefaultSpectrum() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] dg14 = TestChip.dataGroup14(BSIObjectIdentifiers.ecdsa_plain_RIPEMD160);
        byte[] signature = sign(keys, "RIPEMD160withPLAIN-ECDSA", CHALLENGE);

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE, signature));
        assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, null, CHALLENGE, signature),
                "DG14 olmadan RIPEMD160 yelpazede yok, yani reddedilmeli");
    }

    @Test
    void aDataGroup14DeclaringSha384DerIsUsedForASecp384Chip() throws Exception {
        KeyPair keys = ecKeys("secp384r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] dg14 = TestChip.dataGroup14(X9ObjectIdentifiers.ecdsa_with_SHA384);

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE,
                sign(keys, "SHA384withECDSA", CHALLENGE)));
    }

    @Test
    void anUnrecognisedDataGroup14AlgorithmFallsBackToTheSpectrum() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] dg14 = TestChip.dataGroup14(new ASN1ObjectIdentifier("1.2.3.4.5"));

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE, new byte[64]));

        assertTrue(thrown.getMessage().contains("dg14=1.2.3.4.5 (unrecognised)"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("attempted=10"), thrown.getMessage());
    }

    @Test
    void anUnreadableDataGroup14DoesNotBlockVerification() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] dg14 = "bu dg14 degil".getBytes();

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, dg14, CHALLENGE, new byte[64]));

        assertTrue(thrown.getMessage().contains("dg14=unreadable"), thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void theChipKeyIsReadWhateverBerLengthFormWrapsIt(int lengthOctets) throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrapWithLengthForm(keys.getPublic().getEncoded(), lengthOctets);

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));
    }

    @Test
    void anRsaChipKeyWrappedWithAThreeByteLengthIsRead() throws Exception {
        byte[] key = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(rsaChipKeys.getPublic()).getEncoded("DER");
        byte[] dg15 = wrapWithLengthForm(key, 3);

        assertDoesNotThrow(() -> ActiveAuthentication.verify(dg15, CHALLENGE, chipSignsRsa(CHALLENGE)));
    }

    @Test
    void paddingAfterTheDeclaredDataGroup15LengthDoesNotHideTheKey() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] wrapped = wrap(keys.getPublic().getEncoded());
        byte[] padded = java.util.Arrays.copyOf(wrapped, wrapped.length + 4);

        assertDoesNotThrow(() -> ActiveAuthentication.verify(padded, CHALLENGE,
                sign(keys, "SHA256withPLAIN-ECDSA", CHALLENGE)));
    }

    @Test
    void aDataGroup15DeclaringMoreBytesThanItCarriesIsUnreadable() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] wrapped = wrap(keys.getPublic().getEncoded());
        byte[] truncated = java.util.Arrays.copyOf(wrapped, wrapped.length - 10);

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(truncated, CHALLENGE, new byte[64]));

        assertEquals("{identity.chip.key.unreadable}", thrown.getMessageKey());
        assertTrue(thrown.getMessage().contains("dg14=absent"), thrown.getMessage());
    }

    @Test
    void aFailedEcVerificationExplainsItselfWithoutLeakingTheChallengeOrSignature() throws Exception {
        KeyPair keys = ecKeys("brainpoolP256r1");
        byte[] dg15 = wrap(keys.getPublic().getEncoded());
        byte[] signature = sign(keys, "SHA256withPLAIN-ECDSA", new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(dg15, CHALLENGE, signature));

        String detail = thrown.getMessage();
        assertTrue(detail.contains("key=EC"), detail);
        assertTrue(detail.contains("curve=brainpoolP256r1"), detail);
        assertTrue(detail.contains("bits=256"), detail);
        assertTrue(detail.contains("signatureBytes=64"), detail);
        assertTrue(detail.contains("dg14=absent"), detail);
        assertTrue(detail.contains("attempted=10"), detail);
        assertFalse(detail.contains(HexFormat.of().formatHex(signature)), detail);
        assertFalse(detail.contains(Base64.getEncoder().encodeToString(signature)), detail);
        assertFalse(detail.contains(HexFormat.of().formatHex(CHALLENGE)), detail);
        assertFalse(detail.contains(Base64.getEncoder().encodeToString(CHALLENGE)), detail);
        assertFalse(detail.contains(HexFormat.of().formatHex(dg15)), detail);
    }

    @Test
    void aFailedRsaVerificationNamesTheKeySizeAndTheAttempts() throws Exception {
        byte[] signature = chipSignsRsa(new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        PassiveAuthenticationException thrown = assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, signature));

        String detail = thrown.getMessage();
        assertTrue(detail.contains("key=RSA"), detail);
        assertTrue(detail.contains("bits=2048"), detail);
        assertTrue(detail.contains("signatureBytes=256"), detail);
        assertTrue(detail.contains("attempted=10"), detail);
    }

    @Test
    void anRsaChipThatRecoversANonceBesideTheChallengeIsVerified() throws Exception {
        ISO9796d2Signer signer = new ISO9796d2Signer(new RSAEngine(), new SHA1Digest(), true);
        signer.init(true, rsaChipKeys.getPrivate());
        byte[] nonce = new byte[256 - 20 - 2];
        java.util.Arrays.fill(nonce, (byte) 0x5A);
        signer.update(nonce, 0, nonce.length);
        signer.update(CHALLENGE, 0, CHALLENGE.length);
        byte[] signature = signer.generateSignature();

        assertDoesNotThrow(() -> ActiveAuthentication.verify(rsaDg15, CHALLENGE, signature));
        assertThrows(PassiveAuthenticationException.class,
                () -> ActiveAuthentication.verify(rsaDg15, new byte[]{8, 7, 6, 5, 4, 3, 2, 1}, signature));
    }
}
