package com.exskylab.koala.core.dtos.user.response;

import java.util.UUID;

public record StaffPartyDto(UUID id, String firstName, String lastName, String email) {
}
