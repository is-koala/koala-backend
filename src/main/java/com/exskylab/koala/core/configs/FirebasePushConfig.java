package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.FirebasePushProperties;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "true")
public class FirebasePushConfig {

    private static final int FIREBASE_HTTP_ATTEMPTS = 5;
    private static final int CREDENTIAL_HTTP_ATTEMPTS_PER_FIREBASE_ATTEMPT = 1;
    private static final Duration MAX_FIREBASE_RETRY_WAIT = Duration.ofSeconds(60);
    private static final Duration NETWORK_SAFETY_MARGIN = Duration.ofSeconds(15);

    @Bean
    @ConditionalOnMissingBean
    public FirebaseCredentialsProvider firebaseCredentialsProvider(FirebasePushProperties properties) {
        int connectTimeout = positiveMillis(properties.getConnectTimeout(), "connect timeout");
        int readTimeout = positiveMillis(properties.getReadTimeout(), "read timeout");
        int writeTimeout = positiveMillis(properties.getWriteTimeout(), "write timeout");
        BoundedGoogleHttpTransport transport = new BoundedGoogleHttpTransport(
                connectTimeout, readTimeout, writeTimeout);
        return () -> GoogleCredentials.getApplicationDefault(() -> transport);
    }

    @Bean(name = "koalaFirebaseApp", destroyMethod = "delete")
    public FirebaseApp koalaFirebaseApp(FirebasePushProperties properties,
                                        PushDeliveryProperties deliveryProperties,
                                        FirebaseCredentialsProvider credentialsProvider) throws IOException {
        if (!StringUtils.hasText(properties.getProjectId())) {
            throw new IllegalStateException("Firebase project ID is required when push is enabled");
        }

        GoogleCredentials loadedCredentials = credentialsProvider.load();
        if (!(loadedCredentials instanceof ServiceAccountCredentials serviceAccountCredentials)) {
            throw new IllegalStateException("Firebase service account credentials are required when push is enabled");
        }
        ServiceAccountCredentials credentials = serviceAccountCredentials.createWithCustomRetryStrategy(false);
        String credentialProjectId = credentials.getProjectId();
        if (!StringUtils.hasText(credentialProjectId)) {
            throw new IllegalStateException("Firebase credential project is required when push is enabled");
        }
        if (!properties.getProjectId().equals(credentialProjectId)) {
            throw new IllegalStateException("Firebase credential project does not match configured project");
        }

        int connectTimeout = positiveMillis(properties.getConnectTimeout(), "connect timeout");
        int readTimeout = positiveMillis(properties.getReadTimeout(), "read timeout");
        int writeTimeout = positiveMillis(properties.getWriteTimeout(), "write timeout");
        Duration networkEnvelope = Duration.ofMillis((long) connectTimeout + readTimeout + writeTimeout)
                .multipliedBy(FIREBASE_HTTP_ATTEMPTS
                        * (1L + CREDENTIAL_HTTP_ATTEMPTS_PER_FIREBASE_ATTEMPT))
                .plus(MAX_FIREBASE_RETRY_WAIT.multipliedBy(FIREBASE_HTTP_ATTEMPTS - 1L))
                .plus(NETWORK_SAFETY_MARGIN);
        if (deliveryProperties.getClaimTimeout().compareTo(networkEnvelope) <= 0) {
            throw new IllegalStateException("Firebase claim timeout must exceed provider network envelope");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(properties.getProjectId())
                .setHttpTransport(new NetHttpTransport())
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .setWriteTimeout(writeTimeout)
                .build();
        return FirebaseApp.initializeApp(options, "koala-push");
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp koalaFirebaseApp) {
        return FirebaseMessaging.getInstance(koalaFirebaseApp);
    }

    private int positiveMillis(Duration duration, String name) {
        if (duration == null) {
            throw new IllegalStateException("Firebase " + name + " must be a positive integer millisecond value");
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Firebase " + name + " must be a positive integer millisecond value", exception);
        }
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalStateException("Firebase " + name + " must be a positive integer millisecond value");
        }
        return Math.toIntExact(millis);
    }
}
