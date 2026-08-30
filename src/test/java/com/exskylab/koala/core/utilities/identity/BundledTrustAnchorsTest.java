package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import org.junit.jupiter.api.Test;

import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
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
}
