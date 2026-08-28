package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageableSanitizerTest {

    private final PageableSanitizer sanitizer = new PageableSanitizer();
    private final Set<String> allowed = Set.of("createdAt", "salary");
    private final Sort fallback = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void anOversizedPageIsCappedSoOneRequestCannotPullTheWholeTable() {
        Pageable sanitized = sanitizer.sanitize(PageRequest.of(0, 5000), allowed, fallback);

        assertEquals(100, sanitized.getPageSize());
    }

    @Test
    void aNegativePageNumberFallsBackToTheFirstPage() {
        Pageable sanitized = sanitizer.sanitize(Pageable.unpaged(), allowed, fallback);

        assertEquals(0, sanitized.getPageNumber());
        assertTrue(sanitized.getPageSize() > 0);
    }

    @Test
    void anAllowedSortIsKept() {
        Pageable sanitized = sanitizer.sanitize(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "salary")), allowed, fallback);

        assertEquals(Sort.by(Sort.Direction.ASC, "salary"), sanitized.getSort());
    }

    @Test
    void aSortOnAFieldOutsideTheWhitelistIsRejectedNotSilentlyIgnored() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("password"));

        assertThrows(ValidationException.class,
                () -> sanitizer.rejectUnknownSort(requested, allowed));
    }

    @Test
    void anEmptySortFallsBackToTheDefaultOrdering() {
        Pageable sanitized = sanitizer.sanitize(PageRequest.of(2, 10), allowed, fallback);

        assertEquals(fallback, sanitized.getSort());
        assertEquals(2, sanitized.getPageNumber());
    }
}
