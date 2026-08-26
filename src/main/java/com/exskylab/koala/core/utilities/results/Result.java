package com.exskylab.koala.core.utilities.results;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

public class Result {
    private boolean success;
    private String message;
    private HttpStatus httpStatus;
    private String path;
    private LocalDateTime timeStamp = LocalDateTime.now();
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

    public LocalDateTime getTimeStamp(){
        return timeStamp;
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
        this.correlationId = MDC.get("correlationId");
    }

    public Result(boolean success, HttpStatus httpStatus) {
        this.success = success;
        this.httpStatus = httpStatus;
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        this.path = request.getRequestURI();
        this.correlationId = MDC.get("correlationId");
    }
}
