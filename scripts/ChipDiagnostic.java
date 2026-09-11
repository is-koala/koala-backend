import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Selector;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.*;
import java.util.*;

public class ChipDiagnostic {

    static int step = 0;

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        if (args.length < 3) {
            System.out.println("kullanim: verify-chip.sh <sod-dosyasi> <dg1-dosyasi> <csca-dizini>");
            System.exit(2);
        }

        byte[] sod = read(Path.of(args[0]));
        byte[] dg1 = read(Path.of(args[1]));
        Path cscaDir = Path.of(args[2]);

        System.out.printf("SOD  : %d bayt%n", sod.length);
        System.out.printf("DG1  : %d bayt%n", dg1.length);

        Set<TrustAnchor> anchors = loadAnchors(cscaDir);
        step("Guven cipalari yuklendi", anchors.size() + " CSCA sertifikasi", !anchors.isEmpty());
        if (anchors.isEmpty()) { fail("CSCA dizini bos ya da okunamadi: " + cscaDir); }

        if (sod.length > 0 && (sod[0] & 0xFF) == 0x77) {
            int first = sod[1] & 0xFF;
            int off = first < 0x80 ? 2 : 2 + (first & 0x7F);
            sod = Arrays.copyOfRange(sod, off, sod.length);
            System.out.println("       (0x77 application tag soyuldu)");
        }

        CMSSignedData cms;
        try {
            cms = new CMSSignedData(sod);
            step("SOD ayristirildi", "CMS SignedData", true);
        } catch (Exception e) { step("SOD ayristirildi", e.getMessage(), false); return; }

        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        Collection<X509CertificateHolder> matches =
                cms.getCertificates().getMatches((Selector<X509CertificateHolder>) signer.getSID());
        if (matches.isEmpty()) { step("Document Signer bulundu", "SOD icinde sertifika yok", false); return; }

        X509Certificate ds = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(matches.iterator().next());
        step("Document Signer bulundu", ds.getSubjectX500Principal().getName(), true);
        System.out.println("       veren  : " + ds.getIssuerX500Principal().getName());
        System.out.println("       gecerli: " + ds.getNotBefore() + " -> " + ds.getNotAfter());

        boolean signatureOk;
        try {
            signatureOk = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(ds));
        } catch (Exception e) { signatureOk = false; }
        step("SOD imzasi dogrulandi", signatureOk ? "gecerli" : "GECERSIZ", signatureOk);
        if (!signatureOk) return;

        try {
            CertPath path = CertificateFactory.getInstance("X.509", "BC").generateCertPath(List.of(ds));
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            PKIXCertPathValidatorResult r = (PKIXCertPathValidatorResult)
                    CertPathValidator.getInstance("PKIX", "BC").validate(path, params);
            step("CSCA zincirine baglandi",
                 r.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName(), true);
        } catch (Exception e) {
            step("CSCA zincirine baglandi", e.getMessage(), false);
            System.out.println();
            System.out.println("  >> Bu adim BU TESTIN ASIL SORUSU. Basarisizsa kimlik kartinin Document");
            System.out.println("     Signer'i elimizdeki CSCA'lardan hicbirine zincirlenmiyor demektir;");
            System.out.println("     NVI'den kimlik kartina ozel CSCA istenmesi gerekir.");
            return;
        }

        byte[] eContent = (byte[]) cms.getSignedContent().getContent();
        ASN1Sequence lds = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(eContent));
        AlgorithmIdentifier alg = AlgorithmIdentifier.getInstance(lds.getObjectAt(1));
        ASN1Sequence hashes = ASN1Sequence.getInstance(lds.getObjectAt(2));

        byte[] expected = null;
        StringBuilder present = new StringBuilder();
        for (int i = 0; i < hashes.size(); i++) {
            ASN1Sequence e = ASN1Sequence.getInstance(hashes.getObjectAt(i));
            int dg = ASN1Integer.getInstance(e.getObjectAt(0)).intValueExact();
            present.append("DG").append(dg).append(' ');
            if (dg == 1) expected = ASN1OctetString.getInstance(e.getObjectAt(1)).getOctets();
        }
        System.out.println("       SOD'daki veri gruplari: " + present.toString().trim());
        System.out.println("       ozet algoritmasi      : " + alg.getAlgorithm().getId());

        String digestName = switch (alg.getAlgorithm().getId()) {
            case "1.3.14.3.2.26" -> "SHA-1";
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            case "2.16.840.1.101.3.4.2.4" -> "SHA-224";
            default -> null;
        };
        if (digestName == null) { step("DG1 ozeti karsilastirildi", "desteklenmeyen algoritma", false); return; }

        byte[] actual = MessageDigest.getInstance(digestName, "BC").digest(dg1);
        boolean hashOk = expected != null && MessageDigest.isEqual(expected, actual);
        step("DG1 ozeti eslesti", hashOk ? "eslesti" : "ESLESMEDI", hashOk);
        if (!hashOk) return;

        String mrz = extractMrz(dg1);
        System.out.println("       MRZ uzunlugu: " + (mrz == null ? "okunamadi" : mrz.length()));
        if (mrz != null && mrz.length() == 90) {
            System.out.println("       satir 1: " + mrz.substring(0, 30));
            System.out.println("       satir 2: " + mrz.substring(30, 60));
            System.out.println("       satir 3: " + mrz.substring(60, 90));
            String optional = mrz.substring(15, 30).replace("<", "");
            boolean checkDigit = mrz.charAt(14) != '<';
            step("Optional data 11 hane", optional + (checkDigit ? "" : "  (15. karakter dolgu!)"),
                 checkDigit && optional.matches("\\d{11}"));
            System.out.println("       dogum tarihi (satir 2, 1-6): " + mrz.substring(30, 36));
        } else {
            step("MRZ TD1 bicimi", "90 karakter degil", false);
        }

        System.out.println();
        System.out.println("SONUC: zincirin tamami gecti.");
    }

    static void step(String name, String detail, boolean ok) {
        System.out.printf("%n[%d] %-28s %s  %s%n", ++step, name, ok ? "OK  " : "HATA", detail);
    }

    static void fail(String message) { System.out.println("\n" + message); System.exit(1); }

    static byte[] read(Path p) throws Exception {
        byte[] raw = Files.readAllBytes(p);
        String asText = new String(raw, StandardCharsets.US_ASCII).trim();
        if (asText.matches("[A-Za-z0-9+/=\\s]+") && asText.length() > 16) {
            try { return Base64.getDecoder().decode(asText.replaceAll("\\s", "")); }
            catch (IllegalArgumentException ignored) { }
        }
        return raw;
    }

    static Set<TrustAnchor> loadAnchors(Path dir) throws Exception {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (!Files.isDirectory(dir)) return anchors;
        CertificateFactory f = CertificateFactory.getInstance("X.509", "BC");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                if (!Files.isRegularFile(file)) continue;
                try (InputStream in = Files.newInputStream(file)) {
                    for (Certificate c : f.generateCertificates(in)) {
                        if (c instanceof X509Certificate x) anchors.add(new TrustAnchor(x, null));
                    }
                } catch (Exception e) {
                    System.out.println("  ! " + file.getFileName() + " okunamadi: " + e.getMessage());
                }
            }
        }
        return anchors;
    }

    static String extractMrz(byte[] dg1) {
        int i = 0;
        if ((dg1[0] & 0xFF) == 0x61) i = skip(dg1, 0, 1);
        if (i >= 0 && i + 1 < dg1.length && (dg1[i] & 0xFF) == 0x5F && (dg1[i + 1] & 0xFF) == 0x1F) {
            int s = skip(dg1, i, 2);
            if (s < 0) return null;
            return new String(dg1, s, dg1.length - s, StandardCharsets.US_ASCII).replaceAll("\\s", "");
        }
        return new String(dg1, StandardCharsets.US_ASCII).replaceAll("\\s", "");
    }

    static int skip(byte[] b, int off, int tagLen) {
        int lo = off + tagLen;
        if (lo >= b.length) return -1;
        int first = b[lo] & 0xFF;
        return first < 0x80 ? lo + 1 : lo + 1 + (first & 0x7F);
    }
}
