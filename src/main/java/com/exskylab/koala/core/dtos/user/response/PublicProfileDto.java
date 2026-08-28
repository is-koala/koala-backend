package com.exskylab.koala.core.dtos.user.response;

import java.util.UUID;

public record PublicProfileDto(UUID id, String firstName, String lastName, String username,
                               String profilePictureUrl, String jobTitle, String about,
                               String cityName, String districtName, String cvUrl) {
}
