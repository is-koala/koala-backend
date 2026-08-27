package com.exskylab.koala.webAPI;

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
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping
public class ReviewsController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    public ReviewsController(ReviewService reviewService, ReviewMapper reviewMapper) {
        this.reviewService = reviewService;
        this.reviewMapper = reviewMapper;
    }

    @PostMapping("/job-assignments/{assignmentId}/reviews")
    public ResponseEntity<SuccessDataResult<ReviewDto>> leaveReview(
            @PathVariable UUID assignmentId, @RequestBody @Valid CreateReviewDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                reviewMapper.toReviewDto(reviewService.leaveReview(
                        assignmentId, request.getRating(), request.getComment())),
                ReviewMessages.CREATED, HttpStatus.CREATED));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<SuccessDataResult<PageDto<ReviewDto>>> reviewsAbout(
            @PathVariable UUID userId, @PageableDefault(size = 20) Pageable pageable) {

        var page = reviewService.getReviewsAbout(userId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reviewMapper.toReviewDtoList(page.getContent())),
                ReviewMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/users/{userId}/rating-summary")
    public ResponseEntity<SuccessDataResult<RatingSummaryDto>> summary(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                reviewService.getSummaryFor(userId).map(reviewMapper::toRatingSummaryDto).orElse(null),
                ReviewMessages.LISTED, HttpStatus.OK));
    }
}
