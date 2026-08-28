package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.NotificationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;

@Tag(name = "Bildirim takibi", description = "E-posta açılma takip pikseli.")
@RestController
@RequestMapping("/notification-tracking")
public class NotificationTrackingController {

    private final NotificationService notificationService;

    private static final byte[] TRACKING_PIXEL_BYTES = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    public NotificationTrackingController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }


    @SecurityRequirements
    @Operation(summary = "Takip pikseli",
            description = "E-posta gövdesine gömülen 1x1 görsel. İstek geldiğinde bildirim açılmış olarak işaretlenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Piksel döndürüldü")
    })
    @GetMapping("/{id}.png")
    public ResponseEntity<byte[]> trackNotification(@PathVariable UUID id){
        notificationService.setAsOpened(id);
        CacheControl cacheControl = CacheControl.noCache().mustRevalidate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl(cacheControl.getHeaderValue());

        return new ResponseEntity<>(TRACKING_PIXEL_BYTES, headers, HttpStatus.OK);
    }


}
