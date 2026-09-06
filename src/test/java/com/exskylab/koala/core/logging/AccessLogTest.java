package com.exskylab.koala.core.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.configs.RequestLoggingFilter;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccessLogTest {

    private final ClientIpResolver clientIpResolver = new ClientIpResolver(new TrustedProxyProperties());

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void theAccessLineIsWrittenByTheHttpAccessLoggerSoItsLevelCanBeTuned() throws Exception {
        Logger accessLog = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        accessLog.addAppender(appender);

        try {
            UUID userId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, userId);
            request.setRemoteAddr("10.1.2.3");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(200);

            new RequestLoggingFilter(clientIpResolver).doFilter(request, response, new MockFilterChain());

            assertEquals(1, appender.list.size(), "Bir istek bir erisim satiri yazmali");
            ILoggingEvent event = appender.list.getFirst();
            assertEquals("http.access", event.getLoggerName());

            Map<String, String> context = event.getMDCPropertyMap();
            assertEquals("GET", context.get("method"));
            assertEquals("/users/me", context.get("path"));
            assertEquals("200", context.get("status"));
            assertEquals(userId.toString(), context.get("userId"));
            assertEquals("10.1.2.3", context.get("clientIp"));
            assertNotNull(context.get("durationMs"));
        } finally {
            accessLog.detachAppender(appender);
            appender.stop();
        }
    }
}
