package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class PageableSanitizer {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public Pageable sanitize(Pageable requested, Set<String> sortableFields, Sort fallback) {
        if (requested == null || requested.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, fallback);
        }

        int page = Math.max(requested.getPageNumber(), 0);
        int size = requested.getPageSize() < 1 ? DEFAULT_PAGE_SIZE
                : Math.min(requested.getPageSize(), MAX_PAGE_SIZE);

        List<Sort.Order> allowed = requested.getSort().stream()
                .filter(order -> sortableFields.contains(order.getProperty()))
                .toList();

        Sort sort = allowed.isEmpty() ? fallback : Sort.by(allowed);

        return PageRequest.of(page, size, sort);
    }

    public void rejectUnknownSort(Pageable requested, Set<String> sortableFields) {
        if (requested == null || requested.isUnpaged()) {
            return;
        }

        List<String> unknown = requested.getSort().stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !sortableFields.contains(property))
                .toList();

        if (!unknown.isEmpty()) {
            throw new ValidationException("{page.sort.field.not.allowed}");
        }
    }
}
