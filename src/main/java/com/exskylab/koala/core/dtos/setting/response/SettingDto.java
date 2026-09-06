package com.exskylab.koala.core.dtos.setting.response;

import java.time.Instant;

public record SettingDto(String key, String value, String description, Instant updatedAt) {
}
