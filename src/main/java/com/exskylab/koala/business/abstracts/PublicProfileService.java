package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.user.response.PublicProfileDto;

import java.util.UUID;

public interface PublicProfileService {

    PublicProfileDto getPublicProfile(UUID userId);
}
