package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.dtos.dispute.response.DisputeEvidenceDto;
import com.exskylab.koala.core.helpers.SignedFileResponses;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.core.utilities.storage.S3StorageService;
import com.exskylab.koala.core.utilities.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeEvidenceDownloadContractTest {

    private static final String PUBLIC_CDN = "https://cdn.iskoala.com";
    private static final String KEY = "files/dispute-evidence/8f8e1f1a-4a1e-4d0f-9d55-1f6b3a2c9a77.pdf";

    private static final List<String> ADDRESS_LIKE = List.of("url", "key", "bucket", "endpoint", "link");

    private record LeakyEvidenceDto(UUID id, String storageKey) {
    }

    @Test
    void theEvidenceDtoNamesNoStorageAddress() {
        assertEquals(List.of(), addressLikeNamesIn(DisputeEvidenceDto.class),
                "Kanıt DTO'su nesne anahtarını ya da kalıcı bir adresi taşımamalı; indirme ayrı bir uçtan "
                        + "kısa ömürlü imzalı adresle yapılır (§2ab).");
    }

    @Test
    void theScannerCanActuallyGoRed() {
        assertEquals(List.of("storageKey"), addressLikeNamesIn(LeakyEvidenceDto.class),
                "Tarama depo anahtarını görmeli, yoksa DTO sessizce zenginleştiğinde yeşil kalır.");
    }

    @Test
    void aSignedDownloadAddressExpiresWithinTheConfiguredWindow() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("koala-evidence");
        properties.setRegion("eu-central-1");
        properties.setPublicUrl(PUBLIC_CDN);

        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();

        String signed = new S3StorageService(mock(S3Client.class), presigner, properties).temporaryUrl(KEY);

        assertTrue(signed.contains("X-Amz-Expires=" + properties.getSignedUrlMinutes() * 60),
                "İmzalı adresin ömrü storage.signed-url-minutes ile sınırlı olmalı: " + signed);
        assertTrue(properties.getSignedUrlMinutes() <= 15,
                "Varsayılan imza ömrü kısa kalmalı, dakika: " + properties.getSignedUrlMinutes());
        assertFalse(signed.startsWith(PUBLIC_CDN),
                "Kanıt nesnesi CDN üzerinden kalıcı bir adres almamalı: " + signed);
    }

    @Test
    void theDownloadRouteHandsOutTheShortLivedAddressAndNothingElse() {
        StorageService storageService = mock(StorageService.class);
        when(storageService.supportsSignedUrls()).thenReturn(true);
        when(storageService.temporaryUrl(KEY)).thenReturn("https://bucket.s3.amazonaws.com/" + KEY
                + "?X-Amz-Expires=180&X-Amz-Signature=abc");

        ResponseEntity<?> response = new SignedFileResponses(storageService).attachment(KEY, "kanit.pdf");
        URI location = response.getHeaders().getLocation();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertNotNull(location);
        assertTrue(location.toString().contains("X-Amz-Expires="), "adres imzalı ve kısa ömürlü olmalı");
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void withoutSignedUrlSupportTheBytesAreStreamedAsAnAttachment() {
        StorageService storageService = mock(StorageService.class);
        when(storageService.supportsSignedUrls()).thenReturn(false);
        when(storageService.readFile(KEY)).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<?> response = new SignedFileResponses(storageService).attachment(KEY, "Özgeçmişim.pdf");
        String disposition = response.getHeaders().getFirst("Content-Disposition");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"),
                "Kanıt tarayıcıda çizilmemeli, indirilmeli: " + disposition);
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    private static List<String> addressLikeNamesIn(Class<?> type) {
        List<String> names = new ArrayList<>();
        collect(type, new HashSet<>(), names, true);
        return names.stream()
                .filter(name -> {
                    String lowered = name.toLowerCase(Locale.ROOT);
                    return ADDRESS_LIKE.stream().anyMatch(lowered::contains);
                })
                .toList();
    }

    private static void collect(Class<?> type, Set<Class<?>> visited, List<String> names, boolean root) {
        if (type == null || !visited.add(type) || !type.getName().startsWith("com.exskylab.koala")) {
            return;
        }

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
                collect(component.getType(), visited, names, false);
            }
            return;
        }

        if (root) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    names.add(field.getName());
                }
            }
        }
    }
}
