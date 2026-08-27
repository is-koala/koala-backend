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

    ReviewDto toReviewDto(Review review);

    List<ReviewDto> toReviewDtoList(List<Review> reviews);

    @Mapping(target = "userId", source = "user.id")
    RatingSummaryDto toRatingSummaryDto(RatingSummary summary);
}
