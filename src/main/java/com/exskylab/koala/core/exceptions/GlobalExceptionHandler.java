package com.exskylab.koala.core.exceptions;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.monitoring.BackendErrorMonitor;
import com.exskylab.koala.core.utilities.results.ErrorCategory;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.FieldError;
import com.exskylab.koala.core.utilities.results.Result;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageResolver messageResolver;
    private final BackendErrorMonitor backendErrorMonitor;

    public GlobalExceptionHandler(MessageResolver messageResolver, BackendErrorMonitor backendErrorMonitor) {
        this.messageResolver = messageResolver;
        this.backendErrorMonitor = backendErrorMonitor;
    }

    @ExceptionHandler(KoalaException.class)
    public ResponseEntity<Result> handleKoalaException(KoalaException ex) {
        HttpStatus status = ex.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("Koala hatasi: {}", ex.getErrorCode(), ex);
            backendErrorMonitor.capture(ex, ex.getErrorCode());
        } else {
            log.warn("Koala hatasi: {} - {}", ex.getErrorCode(), ex.getMessage());
        }
        return build(status, ex.getMessage(), ex.getErrorCode(), categoryOf(status), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), messageResolver.resolve(error.getDefaultMessage())))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "{validation.failed}", null, ErrorCategory.VALIDATION_FAILED, details);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result> handleValidationException(ValidationException ex) {
        if (IdentityMessages.NO_TRUST_ANCHORS.equals(ex.getMessage())) {
            log.error("Identity verification trust anchors are unavailable: {}", ex.getErrorCode(), ex);
            backendErrorMonitor.capture(ex, ex.getErrorCode());
        }
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getErrorCode(), categoryOf(ex.getHttpStatus()), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Govdesi okunamayan istek [type=HttpMessageNotReadableException, "
                + "errorCode=REQUEST_MALFORMED, requestId={}]", MDC.get(LogContext.REQUEST_ID));
        return build(HttpStatus.BAD_REQUEST, "{request.body.unreadable}", null, ErrorCategory.REQUEST_MALFORMED, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Boyut sinirini asan multipart istek [errorCode=REQUEST_BODY_TOO_LARGE, requestId={}]",
                MDC.get(LogContext.REQUEST_ID));
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "{request.body.too.large}", "REQUEST_BODY_TOO_LARGE",
                ErrorCategory.REQUEST_MALFORMED, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null, ErrorCategory.VALIDATION_FAILED, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null, ErrorCategory.BUSINESS_RULE_VIOLATION, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "{access.denied}", null, ErrorCategory.ACCESS_DENIED, null);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class,
            InsufficientAuthenticationException.class})
    public ResponseEntity<Result> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "{authentication.required}", null, ErrorCategory.AUTHENTICATION_REQUIRED, null);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Result> handleExpiredJwt(ExpiredJwtException ex) {
        return build(HttpStatus.UNAUTHORIZED, "{jwt.expired}", null, ErrorCategory.TOKEN_EXPIRED, null);
    }

    @ExceptionHandler({SignatureException.class, JwtException.class})
    public ResponseEntity<Result> handleJwt(JwtException ex) {
        log.warn("Gecersiz JWT: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "{jwt.invalid}", null, ErrorCategory.INVALID_CREDENTIALS, null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result> handleBadRequestParameter(Exception ex) {
        log.warn("Gecersiz istek parametresi: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "{request.parameter.invalid}", null, ErrorCategory.PARAMETER_MISSING, null);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<Result> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex) {
        log.warn("Gecersiz sorgu kullanimi: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "{page.sort.field.not.allowed}", null,
                ErrorCategory.VALIDATION_FAILED, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result> handleNoResourceFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "{endpoint.not.found}", null, ErrorCategory.ENDPOINT_NOT_FOUND, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "{method.not.allowed}", null, ErrorCategory.METHOD_NOT_ALLOWED, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleUnexpected(Exception ex) {
        log.error("Beklenmeyen hata [requestId={}]", MDC.get(LogContext.REQUEST_ID), ex);
        backendErrorMonitor.capture(ex, "UNEXPECTED");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "{unexpected.error}", null, ErrorCategory.UNEXPECTED, null);
    }

    private ResponseEntity<Result> build(HttpStatus status, String message, String errorCode,
                                         ErrorCategory category, List<FieldError> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResult(messageResolver.resolve(message), status, errorCode, category)
                        .withDetails(details));
    }

    private static ErrorCategory categoryOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCategory.RESOURCE_NOT_FOUND;
            case FORBIDDEN -> ErrorCategory.ACCESS_DENIED;
            case UNAUTHORIZED -> ErrorCategory.AUTHENTICATION_REQUIRED;
            case CONFLICT -> ErrorCategory.DATA_CONFLICT;
            case BAD_REQUEST -> ErrorCategory.VALIDATION_FAILED;
            case PAYLOAD_TOO_LARGE -> ErrorCategory.REQUEST_MALFORMED;
            case TOO_MANY_REQUESTS -> ErrorCategory.RATE_LIMITED;
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> ErrorCategory.UPSTREAM_FAILED;
            default -> ErrorCategory.UNEXPECTED;
        };
    }
}
