package com.exskylab.koala.core.dtos.legal.response;

import com.exskylab.koala.entities.DeletionBlockerType;

import java.util.UUID;

public record DeletionBlockerDto(DeletionBlockerType type, UUID id) {
}
