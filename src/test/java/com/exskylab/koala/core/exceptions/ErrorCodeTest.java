package com.exskylab.koala.core.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    private static final Path EXCEPTIONS = Paths.get("src/main/java/com/exskylab/koala/core/exceptions");

    @Test
    void theErrorCodeComesFromTheMessageKeySoItStaysSpecific() {
        assertEquals("USER_NOT_FOUND", new ResourceNotFoundException("{user.not.found}").getErrorCode());
        assertEquals("PAYMENT_NOT_FOUND", new ResourceNotFoundException("{payment.not.found}").getErrorCode());
        assertEquals("CANCELLATION_ALREADY_CANCELLED",
                new ConflictException("{cancellation.already.cancelled}").getErrorCode());
    }

    @Test
    void aPlainMessageFallsBackToTheClassName() {
        assertEquals("RESOURCE_NOT_FOUND", new ResourceNotFoundException("bulunamadi").getErrorCode());
        assertEquals("VALIDATION", new ValidationException("gecersiz").getErrorCode());
    }

    @Test
    void everySemanticExceptionCarriesItsOwnStatus() {
        assertEquals(HttpStatus.NOT_FOUND, new ResourceNotFoundException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, new ForbiddenException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.CONFLICT, new ConflictException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, new UnauthorizedException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.BAD_REQUEST, new ValidationException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.BAD_GATEWAY, new ExternalServiceException("{a.b}").getHttpStatus());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, new TooManyRequestsException("{a.b}").getHttpStatus());
    }

    @Test
    void theExceptionSurfaceStaysSmallAndSemantic() throws IOException {
        List<String> classes = new ArrayList<>();

        try (Stream<Path> files = Files.list(EXCEPTIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (!name.equals("GlobalExceptionHandler") && !name.equals("KoalaException")) {
                    classes.add(name);
                }
            }
        }

        assertEquals(List.of("ConflictException", "ExternalServiceException", "ForbiddenException",
                        "ResourceNotFoundException", "TooManyRequestsException",
                        "UnauthorizedException", "ValidationException").size(),
                classes.size(),
                "exception yüzeyi yedi semantik tipten ibaret olmalı, entity başına sınıf açılmamalı: " + classes);
    }

    @Test
    void everyExceptionExtendsTheKoalaBaseSoItCarriesAStatus() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.list(EXCEPTIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("GlobalExceptionHandler") || name.equals("KoalaException")) {
                    continue;
                }
                if (!Files.readString(file).contains("extends KoalaException")) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(offenders.isEmpty(), "KoalaException'dan türemeyen exception: " + offenders);
    }
}
