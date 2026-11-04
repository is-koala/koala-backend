package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.utilities.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageDownloadRulesTest {

    private static final String API_URL = "https://api.iskoala.com";
    private static final String IMAGE_KEY = "images/3a9c96da-13d4-481f-9171-fb29c934f49e.png";

    private StorageService storageService;
    private CvService cvService;
    private IdentityDocumentReviewService identityDocuments;
    private StorageDownloadController controller;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAuthenticatedUserId()).thenReturn(UUID.randomUUID());

        cvService = mock(CvService.class);
        identityDocuments = mock(IdentityDocumentReviewService.class);
        controller = new StorageDownloadController(storageService, securityService, cvService, identityDocuments);
    }

    private static HttpServletRequest requestFor(String key) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/uploads/" + key);
        return request;
    }

    @Test
    void anImageIsNeverRedirectedBackToTheEndpointThatServesIt() {
        when(storageService.supportsSignedUrls()).thenReturn(true);
        when(storageService.temporaryUrl(IMAGE_KEY))
                .thenReturn("https://bucket.s3.eu-central-1.amazonaws.com/" + IMAGE_KEY + "?X-Amz-Signature=x");

        ResponseEntity<?> response = controller.downloadImage(requestFor(IMAGE_KEY));
        URI location = response.getHeaders().getLocation();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertNotNull(location);
        assertFalse(location.toString().startsWith(API_URL + "/uploads/"),
                "kendi ucuna yonlendirirse sonsuz redirect olur: " + location);
    }

    @Test
    void withoutSignedUrlSupportTheBytesAreStreamedInstead() {
        when(storageService.supportsSignedUrls()).thenReturn(false);
        when(storageService.readFile(IMAGE_KEY)).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<?> response = controller.downloadImage(requestFor(IMAGE_KEY));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) response.getBody());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        verify(storageService, never()).temporaryUrl(any());
    }

    @Test
    void aPrivateFileIsRefusedWhenItBelongsToSomeoneElse() {
        doThrow(new ResourceNotFoundException("{storage.file.not.found}"))
                .when(cvService).assertCanDownload("files/baskasinin-cv.pdf");

        assertThrows(ResourceNotFoundException.class,
                () -> controller.downloadPrivate(requestFor("files/baskasinin-cv.pdf")));

        verifyNoInteractions(storageService);
    }

    @Test
    void anImageNeedsNoOwnershipCheck() {
        when(storageService.supportsSignedUrls()).thenReturn(false);
        when(storageService.readFile(IMAGE_KEY)).thenReturn(new byte[]{1});

        controller.downloadImage(requestFor(IMAGE_KEY));

        verifyNoInteractions(cvService);
    }

    @Test
    void aPrivateFileFollowsTheSameRouteAsAnImage() {
        when(storageService.supportsSignedUrls()).thenReturn(false);
        when(storageService.readFile("files/cv.pdf")).thenReturn(new byte[]{9});

        ResponseEntity<?> response = controller.downloadPrivate(requestFor("files/cv.pdf"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    }

    @Test
    void aLegacyIdentityPhotoUnderImagesIsConcealedFromThePublicImageRoute() {
        String legacyKey = "images/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0.jpg";
        when(identityDocuments.isIdentityDocumentKey(legacyKey)).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () -> controller.downloadImage(requestFor(legacyKey)));

        verifyNoInteractions(storageService);
    }

    @Test
    void anIdentityDocumentKeyIsNotAddressableUnderAnyUploadsRoute() {
        String key = "identity-documents/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0.jpg";

        assertThrows(ValidationException.class, () -> controller.downloadImage(requestFor(key)));
        assertThrows(ValidationException.class, () -> controller.downloadPublicFile(requestFor(key)));
        assertThrows(ValidationException.class, () -> controller.downloadPrivate(requestFor(key)));
        assertThrows(ValidationException.class,
                () -> controller.downloadImage(requestFor("images/../" + key)));

        verifyNoInteractions(storageService, cvService);
    }

    @Test
    void anUnknownPrefixIsRefusedBeforeTouchingStorage() {
        assertThrows(ValidationException.class,
                () -> controller.downloadImage(requestFor("secrets/passwords.txt")));

        verifyNoInteractions(storageService);
    }
}
