package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.exceptions.ValidationException;

public final class StaffSearchQuery {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    private StaffSearchQuery() {
    }

    public static String normalize(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new ValidationException(AdminMessages.SEARCH_QUERY_TOO_LONG);
        }
        return trimmed.length() < MIN_LENGTH ? null : trimmed;
    }
}
