package com.exskylab.koala.core.dtos.review.request;

import com.exskylab.koala.core.constants.ReviewMessages;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateReviewDto {

    @NotNull(message = ReviewMessages.RATING_RANGE)
    @Min(value = 1, message = ReviewMessages.RATING_RANGE)
    @Max(value = 5, message = ReviewMessages.RATING_RANGE)
    private Integer rating;

    @Size(max = 2000)
    private String comment;
}
