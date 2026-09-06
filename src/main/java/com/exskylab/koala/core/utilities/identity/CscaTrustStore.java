package com.exskylab.koala.core.utilities.identity;

import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Component
@Slf4j
public class CscaTrustStore {

    private static final String BUNDLED = "classpath*:csca/*.der";

    private final IdentityVerificationProperties properties;
    private Set<TrustAnchor> anchors = Set.of();

    public CscaTrustStore(IdentityVerificationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void load() {
        BouncyCastleProviders.register();

        Set<TrustAnchor> loaded = new HashSet<>(readBundled());

        String directory = properties.getTrustAnchorDirectory();
        if (directory != null && !directory.isBlank()) {
            loaded.addAll(readDirectory(Path.of(directory)));
        }

        anchors = Set.copyOf(loaded);

        if (anchors.isEmpty() && properties.isEnabled()) {
            log.error("Passive authentication is enabled but no CSCA trust anchor could be loaded; "
                    + "every identity verification will be refused.");
        } else {
            log.info("Loaded {} CSCA trust anchors", anchors.size());
        }
    }

    public Set<TrustAnchor> anchors() {
        return anchors;
    }

    public PassiveAuthenticationVerifier verifier() {
        return new PassiveAuthenticationVerifier(anchors);
    }

    private Set<TrustAnchor> readBundled() {
        Set<TrustAnchor> loaded = new HashSet<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(BUNDLED);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    loaded.addAll(read(in));
                } catch (Exception e) {
                    log.warn("Bundled CSCA trust anchor {} could not be read: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Bundled CSCA trust anchors could not be listed: {}", e.getMessage());
        }
        return loaded;
    }

    private Set<TrustAnchor> readDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            log.error("CSCA trust anchor directory {} does not exist.", directory);
            return Set.of();
        }

        Set<TrustAnchor> loaded = new HashSet<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try (InputStream in = Files.newInputStream(file)) {
                    loaded.addAll(read(in));
                } catch (Exception e) {
                    log.warn("CSCA trust anchor {} could not be read: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("CSCA trust anchors could not be loaded from {}", directory, e);
        }
        return loaded;
    }

    private static Set<TrustAnchor> read(InputStream in) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509",
                BouncyCastleProvider.PROVIDER_NAME);

        Set<TrustAnchor> loaded = new HashSet<>();
        factory.generateCertificates(in).stream()
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .forEach(certificate -> loaded.add(new TrustAnchor(certificate, null)));
        return loaded;
    }
}
