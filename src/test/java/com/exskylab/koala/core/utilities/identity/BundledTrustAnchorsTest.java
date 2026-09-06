package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BundledTrustAnchorsTest {

    private static Set<TrustAnchor> load() {
        IdentityVerificationProperties properties = new IdentityVerificationProperties();
        CscaTrustStore store = new CscaTrustStore(properties);
        store.load();
        return store.anchors();
    }

    @Test
    void theImageShipsWithTurkishTrustAnchors() {
        Set<TrustAnchor> anchors = load();

        assertFalse(anchors.isEmpty(), "csca/ altindaki sertifikalar imajda gelmeli");
        assertTrue(anchors.stream().allMatch(anchor ->
                        anchor.getTrustedCert().getSubjectX500Principal().getName().contains("C=TR")),
                "yalnizca Turkiye CSCA'lari paketlenmeli");
    }

    @Test
    void everyBundledAnchorParsesEvenWithExplicitCurveParameters() {
        assertTrue(load().size() >= 12,
                "explicit EC parametreli sertifikalar da yuklenmeli; varsayilan saglayici bunlari dusuruyordu");
    }

    @Test
    void atLeastOneAnchorIsStillValidToday() {
        Date now = Date.from(Instant.now());

        assertTrue(load().stream().anyMatch(anchor -> {
            X509Certificate certificate = anchor.getTrustedCert();
            return !now.before(certificate.getNotBefore()) && !now.after(certificate.getNotAfter());
        }), "paketlenen cipalarin hepsi suresi dolmus, liste yenilenmeli");
    }

    private static final String TRAVEL_DOCUMENT_ROOT_V1 =
            "de8f888dc72510723a70f87d6724d6419b822eaa0d141577f3a36f7838a1034b";

    private static String sha256Of(X509Certificate certificate) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (java.security.NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theTravelDocumentRootThatSignsIdentityCardsIsBundled() {
        assertTrue(load().stream().anyMatch(anchor ->
                        TRAVEL_DOCUMENT_ROOT_V1.equals(sha256Of(anchor.getTrustedCert()))),
                "T.C. Seyahat Belgesi kok sertifikasi eksik: kimlik karti Document Signer'i buna "
                        + "zincirleniyor ve o olmadan her dogrulama reddedilir");
    }
}
