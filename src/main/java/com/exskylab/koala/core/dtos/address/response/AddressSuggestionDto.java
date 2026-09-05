package com.exskylab.koala.core.dtos.address.response;

import java.util.UUID;

public record AddressSuggestionDto(UUID id, String mainText, String secondaryText) {
}
