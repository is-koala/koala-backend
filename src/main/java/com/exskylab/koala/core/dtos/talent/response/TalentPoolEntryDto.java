package com.exskylab.koala.core.dtos.talent.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.time.Instant;
import java.util.UUID;

public record TalentPoolEntryDto(UUID id, SafeUserDto worker, UUID companyId,
                                 String note, Instant createdAt) {
}
