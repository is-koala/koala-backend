package com.exskylab.koala.core.exceptions;

import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.FieldError;
import com.exskylab.koala.core.utilities.results.Result;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MessageResolver messageResolver;

    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(KoalaException.class)
    public ResponseEntity<Result> handleKoalaException(KoalaException ex) {
        HttpStatus status = ex.getHttpStatus();
        if (status.is5xxServerError()) {
            logger.error("Koala hatasi: {}", ex.getErrorCode(), ex);
        } else {
            logger.warn("Koala hatasi: {} - {}", ex.getErrorCode(), ex.getMessage());
        }
        return build(status, ex.getMessage(), ex.getErrorCode(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), messageResolver.resolve(error.getDefaultMessage())))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "{validation.failed}", "VALIDATION_FAILED", details);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result> handleValidationException(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "VALIDATION_FAILED", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.warn("Govdesi okunamayan istek: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "{request.body.unreadable}", "MALFORMED_REQUEST_BODY", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_ARGUMENT", null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INVALID_STATE", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "{access.denied}", "ACCESS_DENIED", null);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class,
            InsufficientAuthenticationException.class})
    public ResponseEntity<Result> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "{authentication.required}", "AUTHENTICATION_REQUIRED", null);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Result> handleExpiredJwt(ExpiredJwtException ex) {
        return build(HttpStatus.UNAUTHORIZED, "{jwt.expired}", "JWT_EXPIRED", null);
    }

    @ExceptionHandler({SignatureException.class, JwtException.class})
    public ResponseEntity<Result> handleJwt(JwtException ex) {
        logger.warn("Gecersiz JWT: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "{jwt.invalid}", "JWT_INVALID", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result> handleNoResourceFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "{endpoint.not.found}", "ENDPOINT_NOT_FOUND", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "{method.not.allowed}", "METHOD_NOT_ALLOWED", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleUnexpected(Exception ex) {
        logger.error("Beklenmeyen hata [correlationId={}]", MDC.get("correlationId"), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "{unexpected.error}", "INTERNAL_ERROR", null);
    }

    private ResponseEntity<Result> build(HttpStatus status, String message, String errorCode,
                                         List<FieldError> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResult(messageResolver.resolve(message), status, errorCode, details));
    }
}
