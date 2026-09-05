package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.AddressSearchService;
import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.dtos.address.response.AddressCandidateDto;
import com.exskylab.koala.core.dtos.address.response.AddressSearchSessionDto;
import com.exskylab.koala.core.dtos.address.response.AddressSuggestionDto;
import com.exskylab.koala.core.dtos.address.request.ReverseGeocodeRequestDto;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Tag(name = "Adres arama", description = "Kısa ömürlü, kimlik doğrulamalı adres arama oturumları.")
@RestController
@RequestMapping("/locations")
public class AddressSearchController {
    private final AddressSearchService addressSearchService;

    public AddressSearchController(AddressSearchService addressSearchService) {
        this.addressSearchService = addressSearchService;
    }

    @Operation(summary = "Adres arama oturumu başlat")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Adres arama oturumu oluşturuldu")
    })
    @PostMapping("/address-search-sessions")
    public ResponseEntity<SuccessDataResult<AddressSearchSessionDto>> startSession() {
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                addressSearchService.startSession(), AddressMessages.SEARCH_SESSION_CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Adres önerilerini ara")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres önerileri listelendi"),
            @ApiResponse(responseCode = "400", description = "Arama oturumu veya sorgu geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "Adres sağlayıcısına ulaşılamıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/address-search-sessions/{sessionId}/suggestions")
    public ResponseEntity<SuccessDataResult<List<AddressSuggestionDto>>> suggestions(
            @PathVariable UUID sessionId, @RequestParam String query) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                addressSearchService.search(sessionId, query),
                AddressMessages.SEARCH_SUGGESTIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Adres adayını çözümle")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres adayı çözümlendi"),
            @ApiResponse(responseCode = "400", description = "Aday geçersiz veya İstanbul hizmet alanı dışında",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "Adres sağlayıcısına ulaşılamıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/address-search-sessions/{sessionId}/candidates/{candidateId}/resolve")
    public ResponseEntity<SuccessDataResult<AddressCandidateDto>> resolve(
            @PathVariable UUID sessionId, @PathVariable UUID candidateId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                addressSearchService.resolve(sessionId, candidateId),
                AddressMessages.SEARCH_CANDIDATE_RESOLVED, HttpStatus.OK));
    }

    @Operation(summary = "Harita konumundan adres önerisi oluştur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres önerisi oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Konum hizmet alanı dışında veya eşleşme yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "Adres sağlayıcısına ulaşılamıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/address-candidates/reverse")
    public ResponseEntity<SuccessDataResult<AddressCandidateDto>> reverse(
            @RequestBody @Valid ReverseGeocodeRequestDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                addressSearchService.reverse(request), AddressMessages.REVERSE_RESOLVED, HttpStatus.OK));
    }
}
