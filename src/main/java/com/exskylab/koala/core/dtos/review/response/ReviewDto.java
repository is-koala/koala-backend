package com.exskylab.koala.core.dtos.review.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(UUID id, SafeUserDto author, int rating, String comment, Instant createdAt) {
}
