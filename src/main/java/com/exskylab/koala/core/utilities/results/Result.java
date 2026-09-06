package com.exskylab.koala.core.utilities.results;

import com.exskylab.koala.core.logging.LogContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

public class Result {
    private boolean success;
    private String message;
    private HttpStatus httpStatus;
    private String path;
    private Instant timeStamp = Instant.now();
    private String requestId;
    private String correlationId;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HttpStatus getHttpStatus(){
        return httpStatus;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimeStamp(){
        return timeStamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Result(boolean success, String message, HttpStatus httpStatus) {
        this.success = success;
        this.message = message;
        this.httpStatus = httpStatus;
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        this.path = request.getRequestURI();
        this.requestId = MDC.get(LogContext.REQUEST_ID);
        this.correlationId = this.requestId;
    }

    public Result(boolean success, HttpStatus httpStatus) {
        this.success = success;
        this.httpStatus = httpStatus;
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        this.path = request.getRequestURI();
        this.requestId = MDC.get(LogContext.REQUEST_ID);
        this.correlationId = this.requestId;
    }
}
