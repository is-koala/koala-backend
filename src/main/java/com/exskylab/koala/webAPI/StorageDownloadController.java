package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.utilities.storage.StorageKeys;
import com.exskylab.koala.core.utilities.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Dosya indirme", description = "Depodaki nesnelere erişim.")
@RestController
@Slf4j
public class StorageDownloadController {

    private static final String BASE_PATH = "/uploads/";

    private final StorageService storageService;
    private final SecurityService securityService;
    private final CvService cvService;
    private final IdentityDocumentReviewService identityDocumentReviewService;

    public StorageDownloadController(StorageService storageService, SecurityService securityService,
                                     CvService cvService,
                                     IdentityDocumentReviewService identityDocumentReviewService) {
        this.storageService = storageService;
        this.securityService = securityService;
        this.cvService = cvService;
        this.identityDocumentReviewService = identityDocumentReviewService;
    }

    @Operation(summary = "Özel dosya indir",
            description = "Özgeçmiş gibi özel nesneler. Kimlik doğrulaması ve **sahiplik kontrolü** ister; başkasının dosyası 404 döner. Sonra kısa ömürlü imzalı adrese yönlendirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "S3 kullanılıyorsa imzalı adrese yönlendirilir",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı ya da size ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/files/**")
    public ResponseEntity<?> downloadPrivate(HttpServletRequest request) {
        String key = keyOf(request);
        cvService.assertCanDownload(key);

        log.info("Private download of {} by user {}", key, securityService.getAuthenticatedUserId());
        return serve(key);
    }

    @SecurityRequirements
    @Operation(summary = "Genel dosya indir",
            description = "Giriş istemeyen belgeler. Nesne özel kalır, kısa ömürlü imzalı adrese yönlendirilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "S3 kullanılıyorsa imzalı adrese yönlendirilir",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/public/**")
    public ResponseEntity<?> downloadPublicFile(HttpServletRequest request) {
        return serve(keyOf(request));
    }

    @SecurityRequirements
    @Operation(summary = "Görsel indir",
            description = "Görseller CDN üzerinden doğrudan servis edilir. Kimlik doğrulama (TCKK) fotoğrafları bu "
                    + "uçtan hiçbir koşulda verilmez; eski kayıtlarda images/ altında kalmış olanlar da 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "S3 kullanılıyorsa imzalı adrese yönlendirilir",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/images/**")
    public ResponseEntity<?> downloadImage(HttpServletRequest request) {
        String key = keyOf(request);
        if (identityDocumentReviewService.isIdentityDocumentKey(key)) {
            log.warn("Refused a public image request for an identity document object");
            throw new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND);
        }
        return serve(key);
    }

    private String keyOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String key = StorageKeys.fromRequestPath(uri.substring(uri.indexOf(BASE_PATH) + BASE_PATH.length()));

        if (!StorageKeys.isWellFormed(key)) {
            throw new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND);
        }
        if (!StorageKeys.isKnownPrefix(key)) {
            throw new ValidationException(StorageMessages.UNKNOWN_KEY);
        }
        return key;
    }

    private ResponseEntity<?> serve(String key) {
        if (storageService.supportsSignedUrls()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(storageService.temporaryUrl(key)))
                    .build();
        }

        byte[] bytes = storageService.readFile(key);

        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
