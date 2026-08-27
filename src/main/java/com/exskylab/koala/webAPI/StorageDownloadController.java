package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.utilities.storage.StorageKeys;
import com.exskylab.koala.core.utilities.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class StorageDownloadController {

    private static final String BASE_PATH = "/uploads/";

    private final Logger logger = LoggerFactory.getLogger(StorageDownloadController.class);

    private final StorageService storageService;
    private final StorageUrlResolver storageUrlResolver;
    private final SecurityService securityService;

    public StorageDownloadController(StorageService storageService, StorageUrlResolver storageUrlResolver,
                                     SecurityService securityService) {
        this.storageService = storageService;
        this.storageUrlResolver = storageUrlResolver;
        this.securityService = securityService;
    }

    @GetMapping("/uploads/files/**")
    public ResponseEntity<Void> downloadPrivate(HttpServletRequest request) {
        String key = keyOf(request);
        logger.info("Private download of {} by user {}", key, securityService.getAuthenticatedUserId());
        return redirect(storageService.temporaryUrl(key));
    }

    @GetMapping("/uploads/public/**")
    public ResponseEntity<Void> downloadPublicFile(HttpServletRequest request) {
        return redirect(storageService.temporaryUrl(keyOf(request)));
    }

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
