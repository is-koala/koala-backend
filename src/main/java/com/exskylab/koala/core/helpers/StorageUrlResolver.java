package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.core.utilities.storage.StorageKeys;
import org.springframework.stereotype.Component;

@Component
public class StorageUrlResolver {

    private final String publicUrl;
    private final String apiUrl;

    public StorageUrlResolver(StorageProperties storageProperties, AppProperties appProperties) {
        this.publicUrl = trimTrailingSlash(storageProperties.getPublicUrl());
        this.apiUrl = trimTrailingSlash(appProperties.apiUrl());
    }

    public String urlFor(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }

        String normalized = key.startsWith("/") ? key.substring(1) : key;

        if (StorageKeys.isPrivate(normalized) || StorageKeys.isPublicFile(normalized)) {
            return apiUrl + "/uploads/" + normalized;
        }

        return (publicUrl.isBlank() ? apiUrl + "/uploads" : publicUrl) + "/" + normalized;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
