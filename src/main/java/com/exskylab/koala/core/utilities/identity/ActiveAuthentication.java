package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.constants.IdentityMessages;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECNamedDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.ISO9796d2Signer;
import org.bouncycastle.crypto.util.PublicKeyFactory;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ActiveAuthentication {

    private static final List<Hash> HASHES =
            List.of(Hash.SHA256, Hash.SHA1, Hash.SHA224, Hash.SHA384, Hash.SHA512);

    private static final Map<ASN1ObjectIdentifier, EcdsaScheme> DECLARED_ECDSA_SCHEMES = Map.ofEntries(
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_SHA1, new EcdsaScheme(Hash.SHA1, Encoding.PLAIN)),
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_SHA224, new EcdsaScheme(Hash.SHA224, Encoding.PLAIN)),
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_SHA256, new EcdsaScheme(Hash.SHA256, Encoding.PLAIN)),
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_SHA384, new EcdsaScheme(Hash.SHA384, Encoding.PLAIN)),
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_SHA512, new EcdsaScheme(Hash.SHA512, Encoding.PLAIN)),
            Map.entry(BSIObjectIdentifiers.ecdsa_plain_RIPEMD160, new EcdsaScheme(Hash.RIPEMD160, Encoding.PLAIN)),
            Map.entry(X9ObjectIdentifiers.ecdsa_with_SHA1, new EcdsaScheme(Hash.SHA1, Encoding.DER)),
            Map.entry(X9ObjectIdentifiers.ecdsa_with_SHA224, new EcdsaScheme(Hash.SHA224, Encoding.DER)),
            Map.entry(X9ObjectIdentifiers.ecdsa_with_SHA256, new EcdsaScheme(Hash.SHA256, Encoding.DER)),
            Map.entry(X9ObjectIdentifiers.ecdsa_with_SHA384, new EcdsaScheme(Hash.SHA384, Encoding.DER)),
            Map.entry(X9ObjectIdentifiers.ecdsa_with_SHA512, new EcdsaScheme(Hash.SHA512, Encoding.DER)));

    private static final List<EcdsaScheme> ECDSA_SPECTRUM = HASHES.stream()
            .flatMap(hash -> Stream.of(
                    new EcdsaScheme(hash, Encoding.PLAIN), new EcdsaScheme(hash, Encoding.DER)))
            .toList();

    private ActiveAuthentication() {
    }

    public static void verify(byte[] dataGroup15, byte[] challenge, byte[] signature) {
        verify(dataGroup15, null, challenge, signature);
    }

    public static void verify(byte[] dataGroup15, byte[] dataGroup14, byte[] challenge, byte[] signature) {
        ActiveAuthenticationInfo info = ActiveAuthenticationInfo.from(dataGroup14);
        ChipKey key = ChipKey.read(dataGroup15, info);

        List<Candidate> candidates = key.candidates(info);
        int attempted = 0;
        for (Candidate candidate : candidates) {
            attempted++;
            if (candidate.verifies(challenge, signature)) {
                return;
            }
        }

        throw new PassiveAuthenticationException(IdentityMessages.CHIP_NOT_PRESENT,
                "the chip did not sign the server challenge (" + key.describe()
                        + ", signatureBytes=" + signature.length
                        + ", dg14=" + key.describeDeclared(info)
                        + ", attempted=" + attempted + ")");
    }

    private interface Candidate {
        boolean verifies(byte[] challenge, byte[] signature);
    }

    private record ChipKey(AsymmetricKeyParameter parameter) {

        static ChipKey read(byte[] dataGroup15, ActiveAuthenticationInfo info) {
            AsymmetricKeyParameter parameter = parse(dataGroup15).orElse(null);
            if (parameter instanceof RSAKeyParameters || parameter instanceof ECPublicKeyParameters) {
                return new ChipKey(parameter);
            }
            throw new PassiveAuthenticationException(IdentityMessages.CHIP_KEY_UNREADABLE,
                    "DG15 does not carry a readable public key (dg15Bytes="
                            + (dataGroup15 == null ? 0 : dataGroup15.length)
                            + ", dg14=" + info.describe() + ")");
        }

        private static Optional<AsymmetricKeyParameter> parse(byte[] dataGroup15) {
            try {
                byte[] encoded = LdsFiles.unwrap(dataGroup15, DataGroupTags.DG15);
                SubjectPublicKeyInfo publicKeyInfo =
                        SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
                return Optional.of(PublicKeyFactory.createKey(publicKeyInfo));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        List<Candidate> candidates(ActiveAuthenticationInfo info) {
            if (parameter instanceof RSAKeyParameters rsa) {
                return rsaCandidates(rsa);
            }
            ECPublicKeyParameters ec = (ECPublicKeyParameters) parameter;
            Set<EcdsaScheme> schemes = new LinkedHashSet<>();
            declaredScheme(info).ifPresent(schemes::add);
            schemes.addAll(ECDSA_SPECTRUM);
            return schemes.stream()
                    .<Candidate>map(scheme -> (challenge, signature) -> scheme.verifies(ec, challenge, signature))
                    .toList();
        }

        private static List<Candidate> rsaCandidates(RSAKeyParameters rsa) {
            List<Candidate> candidates = new ArrayList<>();
            for (Hash hash : HASHES) {
                candidates.add((challenge, signature) -> verifyRsa(rsa, hash, false, challenge, signature));
                candidates.add((challenge, signature) -> verifyRsa(rsa, hash, true, challenge, signature));
            }
            return candidates;
        }

        String describe() {
            if (parameter instanceof RSAKeyParameters rsa) {
                return "key=RSA, bits=" + rsa.getModulus().bitLength();
            }
            ECDomainParameters domain = ((ECPublicKeyParameters) parameter).getParameters();
            return "key=EC, curve=" + curveName(domain) + ", bits=" + domain.getCurve().getFieldSize();
        }

        String describeDeclared(ActiveAuthenticationInfo info) {
            Optional<ASN1ObjectIdentifier> declared = info.signatureAlgorithm();
            if (declared.isEmpty()) {
                return info.describe();
            }
            if (parameter instanceof RSAKeyParameters) {
                return declared.get().getId() + " (ignored for RSA)";
            }
            EcdsaScheme scheme = DECLARED_ECDSA_SCHEMES.get(declared.get());
            return declared.get().getId() + " (" + (scheme == null ? "unrecognised" : scheme.label()) + ")";
        }

        private static Optional<EcdsaScheme> declaredScheme(ActiveAuthenticationInfo info) {
            return info.signatureAlgorithm().map(DECLARED_ECDSA_SCHEMES::get);
        }
    }

    private static boolean verifyRsa(RSAKeyParameters key, Hash hash, boolean implicitTrailer, byte[] challenge,
                                     byte[] signature) {
        try {
            ISO9796d2Signer signer = new ISO9796d2Signer(new RSAEngine(), hash.newDigest(), implicitTrailer);
            signer.init(false, key);
            signer.updateWithRecoveredMessage(signature);
            if (signer.hasFullMessage()) {
                return endsWith(signer.getRecoveredMessage(), challenge) && signer.verifySignature(signature);
            }
            signer.update(challenge, 0, challenge.length);
            return signer.verifySignature(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean endsWith(byte[] message, byte[] suffix) {
        return message.length >= suffix.length && MessageDigest.isEqual(
                Arrays.copyOfRange(message, message.length - suffix.length, message.length), suffix);
    }

    private static String curveName(ECDomainParameters domain) {
        if (domain instanceof ECNamedDomainParameters named) {
            String name = ECNamedCurveTable.getName(named.getName());
            return name != null ? name : named.getName().getId();
        }
        for (Enumeration<?> names = ECNamedCurveTable.getNames(); names.hasMoreElements(); ) {
            String name = (String) names.nextElement();
            X9ECParameters candidate = ECNamedCurveTable.getByName(name);
            if (candidate != null
                    && candidate.getCurve().equals(domain.getCurve())
                    && candidate.getG().equals(domain.getG())
                    && candidate.getN().equals(domain.getN())) {
                return "explicit(" + name + ")";
            }
        }
        return "explicit";
    }

    private enum Hash {
        SHA1("SHA1", SHA1Digest::new),
        SHA224("SHA224", SHA224Digest::new),
        SHA256("SHA256", SHA256Digest::new),
        SHA384("SHA384", SHA384Digest::new),
        SHA512("SHA512", SHA512Digest::new),
        RIPEMD160("RIPEMD160", RIPEMD160Digest::new);

        private final String label;
        private final Supplier<Digest> factory;

        Hash(String label, Supplier<Digest> factory) {
            this.label = label;
            this.factory = factory;
        }

        Digest newDigest() {
            return factory.get();
        }

        byte[] digest(byte[] message) {
            Digest digest = newDigest();
            digest.update(message, 0, message.length);
            byte[] out = new byte[digest.getDigestSize()];
            digest.doFinal(out, 0);
            return out;
        }
    }

    private enum Encoding {
        PLAIN, DER
    }

    private record EcdsaScheme(Hash hash, Encoding encoding) {

        String label() {
            return hash.label + "with" + (encoding == Encoding.PLAIN ? "PLAIN-ECDSA" : "ECDSA");
        }

        boolean verifies(ECPublicKeyParameters key, byte[] challenge, byte[] signature) {
            BigInteger[] rs = encoding == Encoding.PLAIN ? plain(key, signature) : der(signature);
            if (rs == null) {
                return false;
            }
            try {
                ECDSASigner signer = new ECDSASigner();
                signer.init(false, key);
                return signer.verifySignature(hash.digest(challenge), rs[0], rs[1]);
            } catch (Exception e) {
                return false;
            }
        }

        private static BigInteger[] plain(ECPublicKeyParameters key, byte[] signature) {
            int half = (key.getParameters().getN().bitLength() + 7) / 8;
            if (signature.length != 2 * half) {
                return null;
            }
            return new BigInteger[]{
                    new BigInteger(1, Arrays.copyOfRange(signature, 0, half)),
                    new BigInteger(1, Arrays.copyOfRange(signature, half, signature.length))};
        }

        private static BigInteger[] der(byte[] signature) {
            try {
                ASN1Sequence sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(signature));
                if (sequence.size() != 2) {
                    return null;
                }
                return new BigInteger[]{
                        ASN1Integer.getInstance(sequence.getObjectAt(0)).getValue(),
                        ASN1Integer.getInstance(sequence.getObjectAt(1)).getValue()};
            } catch (Exception e) {
                return null;
            }
        }
    }
}
