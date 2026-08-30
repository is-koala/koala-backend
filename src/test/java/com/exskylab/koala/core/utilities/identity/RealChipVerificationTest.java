package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

class RealChipVerificationTest {

    @Test
    void aRealChipDumpVerifiesAgainstTheConfiguredTrustAnchors() throws Exception {
        Path chipDir = directoryFrom("KOALA_CHIP_DIR");
        Path cscaDir = directoryFrom("KOALA_CSCA_DIR");

        Assumptions.assumeTrue(chipDir != null && cscaDir != null,
                "KOALA_CHIP_DIR ve KOALA_CSCA_DIR verilmedi, gercek kart dogrulamasi atlandi");

        byte[] sod = decode(chipDir.resolve("sod.b64"));
        byte[] dg1 = decode(chipDir.resolve("dg1.b64"));
        Set<TrustAnchor> anchors = loadAnchors(cscaDir);

        System.out.println("\n=== gercek kart dogrulamasi ===");
        System.out.println("sod        : " + sod.length + " bayt");
        System.out.println("dg1        : " + dg1.length + " bayt");
        System.out.println("guven cipa : " + anchors.size());

        describeDocumentSigner(sod);
        MachineReadableZone.fromDataGroup1(dg1).ifPresentOrElse(
                mrz -> {
                    System.out.println("mrz tckn   : " + mrz.nationalIdentityNumber().orElse("(okunamadi)"));
                    System.out.println("mrz dogum  : " + mrz.birthDate().map(Object::toString).orElse("(okunamadi)"));
                },
                () -> System.out.println("mrz        : TD1 olarak ayristirilamadi"));

        try {
            PassiveAuthenticationResult result =
                    new PassiveAuthenticationVerifier(anchors).verify(sod, dg1);
            System.out.println("SONUC      : DOGRULANDI");
            System.out.println("  tckn     : " + result.nationalIdentityNumber());
            System.out.println("  dogum    : " + result.birthDate());
            System.out.println("  imzalayan: " + result.documentSigner());
            System.out.println("  parmak iz: " + result.evidenceDigest());
        } catch (PassiveAuthenticationException e) {
            System.out.println("SONUC      : REDDEDILDI -> " + e.getMessageKey());
            System.out.println("  ayrinti  : " + e.getMessage());
            throw e;
        }
    }

    private static void describeDocumentSigner(byte[] sod) {
        try {
            byte[] unwrapped = sod;
            if (sod.length > 0 && (sod[0] & 0xFF) == 0x77) {
                int first = sod[1] & 0xFF;
                int offset = first < 0x80 ? 2 : 2 + (first & 0x7F);
                unwrapped = new byte[sod.length - offset];
                System.arraycopy(sod, offset, unwrapped, 0, unwrapped.length);
                System.out.println("sod        : 0x77 application tag soyuldu");
            }

            CMSSignedData signed = new CMSSignedData(unwrapped);
            describeDataGroups(signed);
            signed.getCertificates().getMatches(null).forEach(holder -> {
                X509CertificateHolder certificate = (X509CertificateHolder) holder;
                System.out.println("ds subject : " + certificate.getSubject());
                System.out.println("ds issuer  : " + certificate.getIssuer());
                System.out.println("ds gecerli : " + certificate.getNotBefore() + " -> " + certificate.getNotAfter());
            });
        } catch (Exception e) {
            System.out.println("ds         : sertifika okunamadi -> " + e.getMessage());
        }
    }

    private static void describeDataGroups(CMSSignedData signed) {
        try {
            byte[] eContent = (byte[]) signed.getSignedContent().getContent();
            org.bouncycastle.asn1.ASN1Sequence lds = org.bouncycastle.asn1.ASN1Sequence.getInstance(
                    org.bouncycastle.asn1.ASN1Primitive.fromByteArray(eContent));
            org.bouncycastle.asn1.ASN1Sequence hashes =
                    org.bouncycastle.asn1.ASN1Sequence.getInstance(lds.getObjectAt(2));

            StringBuilder present = new StringBuilder();
            for (int i = 0; i < hashes.size(); i++) {
                org.bouncycastle.asn1.ASN1Sequence entry =
                        org.bouncycastle.asn1.ASN1Sequence.getInstance(hashes.getObjectAt(i));
                present.append("DG")
                       .append(org.bouncycastle.asn1.ASN1Integer.getInstance(entry.getObjectAt(0)).intValueExact())
                       .append(' ');
            }
            System.out.println("kartta      : " + present.toString().trim());
            System.out.println("              (DG11 varsa ad ulusal karakterlerle alinabilir, "
                    + "DG15 varsa Active Authentication mumkun)");
        } catch (Exception e) {
            System.out.println("veri gruplari okunamadi -> " + e.getMessage());
        }
    }

    private static Path directoryFrom(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value);
        return Files.isDirectory(path) ? path : null;
    }

    private static byte[] decode(Path file) throws Exception {
        String content = Files.readString(file).replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }

    private static Set<TrustAnchor> loadAnchors(Path directory) throws Exception {
        BouncyCastleProviders.register();
        CertificateFactory factory = CertificateFactory.getInstance("X.509",
                org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);

        Set<TrustAnchor> anchors = new HashSet<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try (var in = Files.newInputStream(file)) {
                    factory.generateCertificates(in).stream()
                            .filter(X509Certificate.class::isInstance)
                            .map(X509Certificate.class::cast)
                            .forEach(certificate -> anchors.add(new TrustAnchor(certificate, null)));
                } catch (Exception unreadableAnchor) {
                    System.out.println("atlanan cipa: " + file.getFileName() + " -> "
                            + unreadableAnchor.getMessage());
                }
            });
        }
        return anchors;
    }
}
