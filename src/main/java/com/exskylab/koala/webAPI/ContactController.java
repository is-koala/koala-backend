package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.ContactService;
import com.exskylab.koala.core.constants.ContactMessages;
import com.exskylab.koala.core.dtos.contact.request.ContactRequestDto;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "İletişim", description = "Tanıtım sitesinin destek formu. SMTP kimliği bu süreçte kalır.")
@RestController
@RequestMapping("/public")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @SecurityRequirements
    @Operation(summary = "Destek formu gönder",
            description = "Oturum istemez. Gönderim kuyruğa yazılır; SMTP kimliği backend'de kalır. "
                    + "Gizli website alanı doluysa aynı 200 döner ve posta gitmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başvuru alındı"),
            @ApiResponse(responseCode = "400", description = "Alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "E-posta kuyruğa yazılamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/contact")
    public ResponseEntity<SuccessResult> submit(@RequestBody @Valid ContactRequestDto request) {
        contactService.submit(request);
        return ResponseEntity.ok(new SuccessResult(ContactMessages.SUBMITTED, HttpStatus.OK));
    }
}
