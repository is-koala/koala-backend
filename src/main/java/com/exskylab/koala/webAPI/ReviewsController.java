package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.core.constants.ReviewMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.review.request.CreateReviewDto;
import com.exskylab.koala.core.dtos.review.response.RatingSummaryDto;
import com.exskylab.koala.core.dtos.review.response.ReviewDto;
import com.exskylab.koala.core.mappers.ReviewMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Değerlendirmeler", description = "Puan bırakma ve itibar özeti.")
@RestController
@RequestMapping
public class ReviewsController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    public ReviewsController(ReviewService reviewService, ReviewMapper reviewMapper) {
        this.reviewService = reviewService;
        this.reviewMapper = reviewMapper;
    }

    @Operation(summary = "Değerlendirme bırak",
            description = "Yalnızca tamamlanmış işte bırakılabilir. Her taraf bir kez yazabilir. Puan 1 ile 5 arasındadır. Yanıt, değerlendirmenin yazıldığı atamanın kimliğini (jobAssignmentId) taşır; herkese açık listede bu alan null gelir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Değerlendirme kaydedildi"),
            @ApiResponse(responseCode = "400", description = "Puan aralık dışında",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İş tamamlanmamış ya da zaten değerlendirilmiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/job-assignments/{assignmentId}/reviews")
    public ResponseEntity<SuccessDataResult<ReviewDto>> leaveReview(
            @PathVariable UUID assignmentId, @RequestBody @Valid CreateReviewDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                reviewMapper.toOwnReviewDto(reviewService.leaveReview(
                        assignmentId, request.getRating(), request.getComment(),
                        Boolean.TRUE.equals(request.getAnonymous()))),
                ReviewMessages.CREATED, HttpStatus.CREATED));
    }

    @SecurityRequirements
    @Operation(summary = "Kullanıcı hakkındaki değerlendirmeler",
            description = "Sayfalıdır ve herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Değerlendirmeler listelendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<SuccessDataResult<PageDto<ReviewDto>>> reviewsAbout(
            @PathVariable UUID userId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = reviewService.getReviewsAbout(userId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reviewMapper.toReviewDtoList(page.getContent())),
                ReviewMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Puan özeti",
            description = "Ortalama ve yorum sayısı biriktirilerek tutulur, her istekte yeniden hesaplanmaz. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Özet döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/users/{userId}/rating-summary")
    public ResponseEntity<SuccessDataResult<RatingSummaryDto>> summary(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                reviewService.getSummaryFor(userId).map(reviewMapper::toRatingSummaryDto).orElse(null),
                ReviewMessages.LISTED, HttpStatus.OK));
    }
}
