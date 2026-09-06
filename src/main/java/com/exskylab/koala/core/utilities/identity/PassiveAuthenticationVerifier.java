package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.constants.IdentityMessages;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Selector;

import java.security.MessageDigest;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class PassiveAuthenticationVerifier {

    static {
        BouncyCastleProviders.register();
    }

    private final Set<TrustAnchor> trustAnchors;

    public PassiveAuthenticationVerifier(Set<TrustAnchor> trustAnchors) {
        this.trustAnchors = trustAnchors;
    }

    public PassiveAuthenticationResult verify(byte[] securityObject, byte[] dataGroup1) {
        return verify(securityObject, dataGroup1, null);
    }

    public PassiveAuthenticationResult verify(byte[] securityObject, byte[] dataGroup1, byte[] dataGroup11) {
        return verify(securityObject, dataGroup1, dataGroup11, null);
    }

    public PassiveAuthenticationResult verify(byte[] securityObject, byte[] dataGroup1, byte[] dataGroup11,
                                              byte[] dataGroup15) {
        if (trustAnchors.isEmpty()) {
            throw new PassiveAuthenticationException(IdentityMessages.NO_TRUST_ANCHORS,
                    "no CSCA trust anchors are configured");
        }

        CMSSignedData signedData = parse(securityObject);
        X509Certificate documentSigner = verifySignature(signedData);
        chainToTrustAnchor(documentSigner);

        byte[] eContent = (byte[]) signedData.getSignedContent().getContent();
        LdsSecurityObject lds = LdsSecurityObject.parse(eContent);

        byte[] expected = lds.hashForDataGroup(1);
        byte[] actual = digest(lds.hashAlgorithm(), dataGroup1);

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new PassiveAuthenticationException(IdentityMessages.DATA_GROUP_TAMPERED,
                    "DG1 hash does not match the signed security object");
        }

        MachineReadableZone mrz = MachineReadableZone.fromDataGroup1(dataGroup1)
                .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.MRZ_UNREADABLE,
                        "DG1 does not carry a TD1 machine readable zone"));

        String identityNumber = mrz.nationalIdentityNumber()
                .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.MRZ_UNREADABLE,
                        "the machine readable zone carries no national identity number"));

        LocalDate birthDate = mrz.birthDate()
                .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.MRZ_UNREADABLE,
                        "the machine readable zone carries no birth date"));

        AdditionalPersonalDetails additional = null;
        if (dataGroup11 != null && dataGroup11.length > 0) {
            byte[] expectedDg11 = lds.hashForDataGroup(11);
            if (!MessageDigest.isEqual(expectedDg11, digest(lds.hashAlgorithm(), dataGroup11))) {
                throw new PassiveAuthenticationException(IdentityMessages.DATA_GROUP_TAMPERED,
                        "DG11 hash does not match the signed security object");
            }
            additional = AdditionalPersonalDetails.parse(dataGroup11).orElse(null);
        }

        verifyChipKeyIsSigned(lds, dataGroup15);

        return new PassiveAuthenticationResult(identityNumber, birthDate, mrz, additional,
                documentSigner.getSubjectX500Principal().getName(), sha256Hex(securityObject));
    }

    private static void verifyChipKeyIsSigned(LdsSecurityObject lds, byte[] dataGroup15) {
        if (dataGroup15 == null || dataGroup15.length == 0) {
            return;
        }

        byte[] expected = lds.findHashForDataGroup(15)
                .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.CHIP_KEY_UNSIGNED,
                        "a DG15 was supplied but the signed security object lists no DG15 hash"));

        if (!MessageDigest.isEqual(expected, digest(lds.hashAlgorithm(), dataGroup15))) {
            throw new PassiveAuthenticationException(IdentityMessages.DATA_GROUP_TAMPERED,
                    "DG15 hash does not match the signed security object");
        }
    }

    private CMSSignedData parse(byte[] securityObject) {
        try {
            return new CMSSignedData(unwrapApplicationTag(securityObject));
        } catch (Exception e) {
            throw new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE, e.getMessage());
        }
    }

    private X509Certificate verifySignature(CMSSignedData signedData) {
        try {
            SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                    .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE,
                            "the security object carries no signer"));

            Collection<X509CertificateHolder> matches =
                    signedData.getCertificates().getMatches((Selector<X509CertificateHolder>) signer.getSID());

            X509CertificateHolder holder = matches.stream().findFirst()
                    .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE,
                            "the document signer certificate is not embedded in the security object"));

            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);

            boolean valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(certificate));

            if (!valid) {
                throw new PassiveAuthenticationException(IdentityMessages.SIGNATURE_INVALID,
                        "the security object signature did not verify");
            }

            return certificate;
        } catch (PassiveAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new PassiveAuthenticationException(IdentityMessages.SIGNATURE_INVALID, e.getMessage());
        }
    }

    private void chainToTrustAnchor(X509Certificate documentSigner) {
        try {
            CertPath path = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME)
                    .generateCertPath(List.of(documentSigner));
            PKIXParameters parameters = new PKIXParameters(trustAnchors);
            parameters.setRevocationEnabled(false);

            CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)
                    .validate(path, parameters);
        } catch (Exception e) {
            throw new PassiveAuthenticationException(IdentityMessages.UNTRUSTED_DOCUMENT_SIGNER, e.getMessage());
        }
    }

    private static byte[] digest(AlgorithmIdentifier algorithm, byte[] content) {
        try {
            String name = DigestNames.of(algorithm.getAlgorithm().getId());
            return MessageDigest.getInstance(name, BouncyCastleProvider.PROVIDER_NAME).digest(content);
        } catch (Exception e) {
            throw new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE, e.getMessage());
        }
    }

    private static byte[] unwrapApplicationTag(byte[] securityObject) {
        if (securityObject.length > 0 && (securityObject[0] & 0xFF) == 0x77) {
            int first = securityObject[1] & 0xFF;
            int offset = first < 0x80 ? 2 : 2 + (first & 0x7F);
            byte[] unwrapped = new byte[securityObject.length - offset];
            System.arraycopy(securityObject, offset, unwrapped, 0, unwrapped.length);
            return unwrapped;
        }
        return securityObject;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private record LdsSecurityObject(AlgorithmIdentifier hashAlgorithm, ASN1Sequence dataGroupHashes) {

        static LdsSecurityObject parse(byte[] eContent) {
            try {
                ASN1Sequence sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(eContent));
                return new LdsSecurityObject(
                        AlgorithmIdentifier.getInstance(sequence.getObjectAt(1)),
                        ASN1Sequence.getInstance(sequence.getObjectAt(2)));
            } catch (Exception e) {
                throw new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE, e.getMessage());
            }
        }

        byte[] hashForDataGroup(int number) {
            return findHashForDataGroup(number)
                    .orElseThrow(() -> new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE,
                            "the security object carries no hash for data group " + number));
        }

        Optional<byte[]> findHashForDataGroup(int number) {
            for (int i = 0; i < dataGroupHashes.size(); i++) {
                ASN1Sequence entry = ASN1Sequence.getInstance(dataGroupHashes.getObjectAt(i));
                if (ASN1Integer.getInstance(entry.getObjectAt(0)).intValueExact() == number) {
                    return Optional.of(ASN1OctetString.getInstance(entry.getObjectAt(1)).getOctets());
                }
            }
            return Optional.empty();
        }
    }

    private static final class DigestNames {
        static String of(String oid) {
            return switch (oid) {
                case "1.3.14.3.2.26" -> "SHA-1";
                case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
                case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
                case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
                case "2.16.840.1.101.3.4.2.4" -> "SHA-224";
                default -> throw new PassiveAuthenticationException(IdentityMessages.SOD_UNREADABLE,
                        "unsupported digest algorithm " + oid);
            };
        }
    }
}
