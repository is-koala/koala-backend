package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.NotificationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/notification-tracking")
public class NotificationController {

    private final NotificationService notificationService;

    private static final byte[] TRACKING_PIXEL_BYTES = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }


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
