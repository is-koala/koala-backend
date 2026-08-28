package com.exskylab.koala.core.utilities.results;

import com.exskylab.koala.core.helpers.RequestResourceResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResult extends Result {

    private final String errorCode;
    private final ErrorCategory errorCategory;
    private final String resource;
    private final String resourceId;
    private List<FieldError> details;

    public ErrorResult(String message, HttpStatus httpStatus, String errorCode, ErrorCategory errorCategory) {
        super(false, message, httpStatus);
        this.errorCode = errorCode == null ? errorCategory.name() : errorCode;
        this.errorCategory = errorCategory;

        RequestResourceResolver.Target target = RequestResourceResolver.resolve();
        this.resource = target.resource();
        this.resourceId = target.id();
    }

    public ErrorResult withDetails(List<FieldError> details) {
        this.details = details == null || details.isEmpty() ? null : List.copyOf(details);
        return this;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public String getResource() {
        return resource;
    }

    public String getResourceId() {
        return resourceId;
    }

    public List<FieldError> getDetails() {
        return details;
    }
}
