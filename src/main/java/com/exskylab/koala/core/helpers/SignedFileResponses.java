package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.utilities.storage.FileDispositions;
import com.exskylab.koala.core.utilities.storage.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class SignedFileResponses {

    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NO_SNIFF = "nosniff";

    private final StorageService storageService;

    public SignedFileResponses(StorageService storageService) {
        this.storageService = storageService;
    }

    public ResponseEntity<?> attachment(String key, String originalFileName) {
        if (storageService.supportsSignedUrls()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(storageService.temporaryUrl(key)))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(CONTENT_TYPE_OPTIONS, NO_SNIFF)
                    .build();
        }

        byte[] bytes = storageService.readFile(key);

        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, FileDispositions.attachment(originalFileName))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(CONTENT_TYPE_OPTIONS, NO_SNIFF)
                .body(bytes);
    }
}
