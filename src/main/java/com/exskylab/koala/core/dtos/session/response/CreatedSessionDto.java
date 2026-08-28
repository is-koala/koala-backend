package com.exskylab.koala.core.dtos.session.response;

import com.exskylab.koala.entities.Session;

public record CreatedSessionDto (
        Session session,

        String originalToken,

        String originalRefreshToken
) {}