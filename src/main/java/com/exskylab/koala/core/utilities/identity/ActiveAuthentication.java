package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.constants.IdentityMessages;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.signers.ISO9796d2Signer;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

public final class ActiveAuthentication {

    private ActiveAuthentication() {
    }

    public static void verify(byte[] dataGroup15, byte[] challenge, byte[] signature) {
        BouncyCastleProviders.register();

        PublicKey publicKey = readPublicKey(dataGroup15);

        boolean verified = "RSA".equals(publicKey.getAlgorithm())
                ? verifyRsa(dataGroup15, challenge, signature)
                : verifyEcdsa(publicKey, challenge, signature);

        if (!verified) {
            throw new PassiveAuthenticationException(IdentityMessages.CHIP_NOT_PRESENT,
                    "the chip did not sign the server challenge");
        }
    }

    private static PublicKey readPublicKey(byte[] dataGroup15) {
        byte[] encoded = unwrapDataGroup15(dataGroup15);

        for (String algorithm : List.of("RSA", "EC")) {
            try {
                return KeyFactory.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME)
                        .generatePublic(new X509EncodedKeySpec(encoded));
            } catch (Exception ignored) {
                continue;
            }
        }

        throw new PassiveAuthenticationException(IdentityMessages.CHIP_KEY_UNREADABLE,
                "DG15 does not carry a readable public key");
    }

    private static boolean verifyRsa(byte[] dataGroup15, byte[] challenge, byte[] signature) {
        PublicKey publicKey = readPublicKey(dataGroup15);

        for (String algorithm : List.of("SHA1withRSA/ISO9796-2", "SHA256withRSA/ISO9796-2")) {
            try {
                Signature verifier = Signature.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
                verifier.initVerify(publicKey);
                verifier.update(challenge);

                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (Exception ignored) {
                continue;
            }
        }

        return verifyRsaWithRecoveredMessage(dataGroup15, challenge, signature);
    }

    private static boolean verifyRsaWithRecoveredMessage(byte[] dataGroup15, byte[] challenge,
                                                         byte[] signature) {
        for (org.bouncycastle.crypto.Digest digest : List.of(new SHA1Digest(), new SHA256Digest())) {
            try {
                AsymmetricKeyParameter key = PublicKeyFactory.createKey(unwrapDataGroup15(dataGroup15));
                ISO9796d2Signer signer = new ISO9796d2Signer(new RSAEngine(), digest, true);
                signer.init(false, key);
                signer.updateWithRecoveredMessage(signature);
                signer.update(challenge, 0, challenge.length);

                if (signer.verifySignature(signature)) {
                    return true;
                }
            } catch (Exception ignored) {
                continue;
            }
        }
        return false;
    }

    private static boolean verifyEcdsa(PublicKey publicKey, byte[] challenge, byte[] signature) {
        for (String algorithm : List.of("SHA256withPLAIN-ECDSA", "SHA1withPLAIN-ECDSA", "SHA256withECDSA")) {
            try {
                Signature verifier = Signature.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
                verifier.initVerify(publicKey);
                verifier.update(challenge);

                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (Exception ignored) {
                continue;
            }
        }
        return false;
    }

    private static byte[] unwrapDataGroup15(byte[] dataGroup15) {
        if (dataGroup15.length > 2 && (dataGroup15[0] & 0xFF) == DataGroupTags.DG15) {
            int first = dataGroup15[1] & 0xFF;
            int offset = first < 0x80 ? 2 : 2 + (first & 0x7F);
            byte[] unwrapped = new byte[dataGroup15.length - offset];
            System.arraycopy(dataGroup15, offset, unwrapped, 0, unwrapped.length);
            return unwrapped;
        }
        return dataGroup15;
    }
}
