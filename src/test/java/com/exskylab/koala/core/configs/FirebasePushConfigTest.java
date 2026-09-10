package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.FirebasePushProperties;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.FirebasePushService;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.core.utilities.push.UnconfiguredPushService;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(FirebasePushProperties.class, PushDeliveryProperties.class, FirebasePushConfig.class,
                    FirebasePushService.class, UnconfiguredPushService.class);

    @Test
    void providerIsDisabledByDefaultWithoutTouchingApplicationDefaultCredentials() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PushService.class);
            assertThat(context).hasSingleBean(UnconfiguredPushService.class);
            assertThat(context).doesNotHaveBean(FirebaseCredentialsProvider.class);
        });
    }

    @Test
    void enabledProviderFailsClosedWithoutAProjectId() {
        contextRunner.withPropertyValues("push.firebase.enabled=true")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("project ID is required"));
    }

    @Test
    void enabledProviderFailsClosedWhenCredentialsCannotBeLoaded() {
        contextRunner.withBean(FirebaseCredentialsProvider.class,
                        () -> () -> {
                            throw new IOException("credential source unavailable");
                        })
                .withPropertyValues("push.firebase.enabled=true", "push.firebase.project-id=koala-prod")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("credential source unavailable"));
    }

    @Test
    void enabledProviderFailsClosedForACredentialProjectMismatch() throws Exception {
        ServiceAccountCredentials credentials = credentials("another-project");
        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues("push.firebase.enabled=true", "push.firebase.project-id=koala-prod")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("credential project does not match"));
    }

    @Test
    void enabledProviderFailsClosedWhenCredentialProjectCannotBeVerified() throws Exception {
        ServiceAccountCredentials credentials = credentials(null);

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues("push.firebase.enabled=true", "push.firebase.project-id=koala-prod")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("credential project is required"));
    }

    @Test
    void enabledProviderRejectsAClaimTimeoutShorterThanItsNetworkEnvelope() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues(
                        "push.firebase.enabled=true",
                        "push.firebase.project-id=koala-staging",
                        "push.delivery.claim-timeout=30s")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("claim timeout must exceed provider network envelope"));
    }

    @Test
    void claimEnvelopeIncludesOneBoundedCredentialRefreshAttempt() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues(
                        "push.firebase.enabled=true",
                        "push.firebase.project-id=koala-staging",
                        "push.delivery.claim-timeout=150s")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("claim timeout must exceed provider network envelope"));
    }

    @Test
    void claimEnvelopeIncludesMaximumSdkRetryWaitsAndRepeatedCredentialRefreshes() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues(
                        "push.firebase.enabled=true",
                        "push.firebase.project-id=koala-staging",
                        "push.delivery.claim-timeout=505s")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("claim timeout must exceed provider network envelope"));
    }

    @Test
    void enabledProviderRejectsPositiveSubMillisecondTimeoutsThatWouldDisableTheSdkTimeout() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues(
                        "push.firebase.enabled=true",
                        "push.firebase.project-id=koala-staging",
                        "push.firebase.write-timeout=1ns")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("write timeout must be a positive integer millisecond value"));
    }

    @Test
    void matchingConfigurationSelectsOnlyTheFirebaseProvider() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues("push.firebase.enabled=true", "push.firebase.project-id=koala-staging")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushService.class);
                    assertThat(context).hasSingleBean(FirebasePushService.class);
                    assertThat(context).doesNotHaveBean(UnconfiguredPushService.class);
                    assertThat(context.getBean(FirebaseApp.class).getOptions().getHttpTransport())
                            .isInstanceOf(NetHttpTransport.class);
                    verify(credentials).createWithCustomRetryStrategy(false);
                });
    }

    @Test
    void documentedExampleTimeoutCanEnableTheProviderWithItsDocumentedNetworkTimeouts() throws Exception {
        ServiceAccountCredentials credentials = credentials("koala-staging");

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues(
                        "push.firebase.enabled=true",
                        "push.firebase.project-id=koala-staging",
                        "push.firebase.connect-timeout=" + exampleValue("FIREBASE_CONNECT_TIMEOUT"),
                        "push.firebase.read-timeout=" + exampleValue("FIREBASE_READ_TIMEOUT"),
                        "push.firebase.write-timeout=" + exampleValue("FIREBASE_WRITE_TIMEOUT"),
                        "push.delivery.claim-timeout=" + exampleValue("PUSH_CLAIM_TIMEOUT"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledProviderRejectsAmbientNonServiceAccountCredentials() {
        GoogleCredentials credentials = mock(GoogleCredentials.class);

        contextRunner.withBean(FirebaseCredentialsProvider.class, () -> () -> credentials)
                .withPropertyValues("push.firebase.enabled=true", "push.firebase.project-id=koala-staging")
                .run(context -> assertThat(rootCause(context.getStartupFailure()).getMessage())
                        .contains("service account credentials are required"));
    }

    private ServiceAccountCredentials credentials(String projectId) {
        ServiceAccountCredentials credentials = mock(ServiceAccountCredentials.class);
        when(credentials.createWithCustomRetryStrategy(false)).thenReturn(credentials);
        when(credentials.getProjectId()).thenReturn(projectId);
        return credentials;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String exampleValue(String key) throws IOException {
        String prefix = key + "=";
        return Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing example setting: " + key));
    }
}
