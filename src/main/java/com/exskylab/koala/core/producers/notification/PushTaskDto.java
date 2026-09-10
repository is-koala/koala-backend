package com.exskylab.koala.core.producers.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushTaskDto(
        UUID id
) implements Serializable {}
