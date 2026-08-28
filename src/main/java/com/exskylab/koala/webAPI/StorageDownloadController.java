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
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.utilities.storage.StorageKeys;
import com.exskylab.koala.core.utilities.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.http.HttpStatus;
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
    private final StorageUrlResolver storageUrlResolver;
    private final SecurityService securityService;

    public StorageDownloadController(StorageService storageService, StorageUrlResolver storageUrlResolver,
                                     SecurityService securityService) {
        this.storageService = storageService;
        this.storageUrlResolver = storageUrlResolver;
        this.securityService = securityService;
    }

    @Operation(summary = "Özel dosya indir",
            description = "Özgeçmiş ve kimlik belgesi gibi nesneler. Kimlik doğrulaması ister, sonra kısa ömürlü imzalı adrese yönlendirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "İmzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/files/**")
    public ResponseEntity<Void> downloadPrivate(HttpServletRequest request) {
        String key = keyOf(request);
        log.info("Private download of {} by user {}", key, securityService.getAuthenticatedUserId());
        return redirect(storageService.temporaryUrl(key));
    }

    @SecurityRequirements
    @Operation(summary = "Genel dosya indir",
            description = "Giriş istemeyen belgeler. Nesne özel kalır, kısa ömürlü imzalı adrese yönlendirilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "İmzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/public/**")
    public ResponseEntity<Void> downloadPublicFile(HttpServletRequest request) {
        return redirect(storageService.temporaryUrl(keyOf(request)));
    }

    @SecurityRequirements
    @Operation(summary = "Görsel indir",
            description = "Görseller CDN üzerinden doğrudan servis edilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Görsel adresine yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "400", description = "Tanınmayan depo öneki",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/uploads/images/**")
    public ResponseEntity<Void> downloadImage(HttpServletRequest request) {
        return redirect(storageUrlResolver.urlFor(keyOf(request)));
    }

    private String keyOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String key = StorageKeys.fromRequestPath(uri.substring(uri.indexOf(BASE_PATH) + BASE_PATH.length()));

        if (!StorageKeys.isKnownPrefix(key)) {
            throw new ValidationException(StorageMessages.UNKNOWN_KEY);
        }
        return key;
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
