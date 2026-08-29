package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.review.response.RatingSummaryDto;
import com.exskylab.koala.core.dtos.review.response.ReviewDto;
import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.Review;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ReviewMapper {

    ReviewDto toNamedReviewDto(Review review);

    @Mapping(target = "author", ignore = true)
    ReviewDto toAnonymousReviewDto(Review review);

    default ReviewDto toReviewDto(Review review) {
        if (review == null) {
            return null;
        }
        return review.isAnonymous() ? toAnonymousReviewDto(review) : toNamedReviewDto(review);
    }

    default List<ReviewDto> toReviewDtoList(List<Review> reviews) {
        if (reviews == null) {
            return null;
        }
        return reviews.stream().map(this::toReviewDto).toList();
    }

    @Mapping(target = "userId", source = "user.id")
    RatingSummaryDto toRatingSummaryDto(RatingSummary summary);
}
