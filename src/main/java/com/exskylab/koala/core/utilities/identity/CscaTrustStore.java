package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Component
@Slf4j
public class CscaTrustStore {

    private final IdentityVerificationProperties properties;
    private Set<TrustAnchor> anchors = Set.of();

    public CscaTrustStore(IdentityVerificationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void load() {
        BouncyCastleProviders.register();

        String directory = properties.getTrustAnchorDirectory();

        if (directory == null || directory.isBlank()) {
            if (properties.isEnabled()) {
                log.error("Passive authentication is enabled but no CSCA trust anchor directory is set; "
                        + "every identity verification will be refused.");
            }
            return;
        }

        Path path = Path.of(directory);
        if (!Files.isDirectory(path)) {
            log.error("CSCA trust anchor directory {} does not exist; passive authentication cannot verify.", path);
            return;
        }

        Set<TrustAnchor> loaded = new HashSet<>();
        try (Stream<Path> files = Files.list(path)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509",
                    BouncyCastleProvider.PROVIDER_NAME);
            files.filter(Files::isRegularFile).forEach(file -> {
                try (InputStream in = Files.newInputStream(file)) {
                    Collection<? extends java.security.cert.Certificate> certificates =
                            factory.generateCertificates(in);
                    certificates.stream()
                            .filter(X509Certificate.class::isInstance)
                            .map(X509Certificate.class::cast)
                            .forEach(certificate -> loaded.add(new TrustAnchor(certificate, null)));
                } catch (Exception e) {
                    log.warn("CSCA trust anchor {} could not be read: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("CSCA trust anchors could not be loaded from {}: {}", path, e.getMessage());
            return;
        }

        anchors = Set.copyOf(loaded);
        log.info("Loaded {} CSCA trust anchors from {}", anchors.size(), path);
    }

    public Set<TrustAnchor> anchors() {
        return anchors;
    }

    public PassiveAuthenticationVerifier verifier() {
        return new PassiveAuthenticationVerifier(anchors);
    }
}
