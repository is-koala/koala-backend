package com.exskylab.koala.core.utilities.identity;

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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

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
}
