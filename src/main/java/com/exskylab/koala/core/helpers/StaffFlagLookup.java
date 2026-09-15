package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ValidationException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StaffFlagLookup {

    public static final int MAX_RESOURCES = 200;

    private StaffFlagLookup() {
    }

    public static Set<UUID> resourceIdsOf(UUID resourceId, List<UUID> resourceIds) {
        Set<UUID> resolved = new LinkedHashSet<>();
        if (resourceId != null) {
            resolved.add(resourceId);
        }
        if (resourceIds != null) {
            resourceIds.stream().filter(id -> id != null).forEach(resolved::add);
        }
        if (resolved.isEmpty()) {
            throw new ValidationException(StaffMessages.FLAG_RESOURCE_REQUIRED);
        }
        if (resolved.size() > MAX_RESOURCES) {
            throw new ValidationException(StaffMessages.FLAG_BATCH_TOO_LARGE);
        }
        return resolved;
    }
}
