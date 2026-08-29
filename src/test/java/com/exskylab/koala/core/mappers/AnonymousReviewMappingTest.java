package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.review.response.ReviewDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.entities.Review;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnonymousReviewMappingTest {

    private static final MediaUrlResolver URLS = new MediaUrlResolver(new StorageUrlResolver(
            new StorageProperties(), new AppProperties("http://localhost:8080", "http://localhost:3000")));

    private final ReviewMapper mapper = new ReviewMapperImpl(new UserMapperImpl(
            URLS, new AddressMapperImpl(), new EducationMapperImpl(), new ExperienceMapperImpl()));

    private static Review review(boolean anonymous) {
        User author = new User();
        author.setId(UUID.randomUUID());
        author.setFirstName("Yusuf");

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setAuthor(author);
        review.setRating(5);
        review.setComment("iyiydi");
        review.setAnonymous(anonymous);
        return review;
    }

    @Test
    void aNamedReviewCarriesItsAuthor() {
        ReviewDto dto = mapper.toReviewDto(review(false));

        assertNotNull(dto.author());
        assertFalse(dto.anonymous());
    }

    @Test
    void anAnonymousReviewHidesItsAuthor() {
        ReviewDto dto = mapper.toReviewDto(review(true));

        assertNull(dto.author());
        assertTrue(dto.anonymous());
        assertEquals("iyiydi", dto.comment());
        assertEquals(5, dto.rating());
    }

    @Test
    void theListMappingHidesAuthorsToo() {
        List<ReviewDto> dtos = mapper.toReviewDtoList(List.of(review(true), review(false)));

        assertNull(dtos.get(0).author());
        assertNotNull(dtos.get(1).author());
    }
}
