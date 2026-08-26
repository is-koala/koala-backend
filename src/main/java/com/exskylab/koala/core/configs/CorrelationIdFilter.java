package com.exskylab.koala.core.configs;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {


    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";


    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static String sanitize(String candidate) {
        if (candidate == null || !SAFE_ID.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String correlationId = sanitize(request.getHeader(CORRELATION_ID_HEADER_NAME));


        MDC.put(MDC_KEY, correlationId);



        response.addHeader(CORRELATION_ID_HEADER_NAME, correlationId);


        try{
            filterChain.doFilter(request, response);
        }finally {
            MDC.remove(MDC_KEY);
        }


    }
}
