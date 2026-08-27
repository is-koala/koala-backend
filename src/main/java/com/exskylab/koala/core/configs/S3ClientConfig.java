package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ClientConfig {

    private final StorageProperties storageProperties;

    public S3ClientConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentialsProvider());

        if (storageProperties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .chunkedEncodingEnabled(false)
                            .build());
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentialsProvider());

        if (storageProperties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .chunkedEncodingEnabled(false)
                            .build());
        }

        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (storageProperties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storageProperties.getAccessKey(), storageProperties.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
