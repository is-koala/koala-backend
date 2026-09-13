package com.exskylab.koala.core.exceptions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.helpers.MessageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorResponseContentTypeTest {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final String threadName = Thread.currentThread().getName();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appender.start();
        rootLogger.addAppender(appender);
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler(
                        new MessageResolver(new StaticMessageSource()), (error, code) -> { }))
                .build();
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "*/*;q=0", "image/png", "text/html,application/xhtml+xml;q=0.9"})
    void anAuthenticationFailureIsJsonWhateverTheClientAccepts(String accept) throws Exception {
        mockMvc.perform(post("/test/anonymous").header(HttpHeaders.ACCEPT, accept))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.httpStatus").value("401 UNAUTHORIZED"))
                .andExpect(jsonPath("$.errorCategory").value("AUTHENTICATION_REQUIRED"));

        assertNoStackTraceAndNoFrameworkWarning();
    }

    @Test
    void anAccessDeniedFailureIsJsonWhenTheClientAcceptsOnlyHtml() throws Exception {
        mockMvc.perform(get("/test/forbidden").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));

        assertNoStackTraceAndNoFrameworkWarning();
    }

    @Test
    void aDomainFailureIsJsonWhenTheClientAcceptsOnlyHtml() throws Exception {
        mockMvc.perform(get("/test/not-found").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.errorCategory").value("RESOURCE_NOT_FOUND"));

        assertNoStackTraceAndNoFrameworkWarning();
    }

    @Test
    void aClientThatAcceptsJsonStillGetsJson() throws Exception {
        mockMvc.perform(post("/test/anonymous").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCategory").value("AUTHENTICATION_REQUIRED"));
    }

    private void assertNoStackTraceAndNoFrameworkWarning() {
        List<ILoggingEvent> onThisThread = appender.list.stream()
                .filter(event -> threadName.equals(event.getThreadName()))
                .toList();

        List<ILoggingEvent> withStackTrace = onThisThread.stream()
                .filter(event -> event.getThrowableProxy() != null)
                .toList();
        assertTrue(withStackTrace.isEmpty(), "Hata yaniti uretilirken yigin izi loglanmamali: " + withStackTrace);

        List<ILoggingEvent> frameworkWarnings = onThisThread.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .filter(event -> event.getLoggerName().startsWith("org.springframework"))
                .toList();
        assertEquals(List.of(), frameworkWarnings,
                "Istisna isleyicisi icerik muzakeresine takilmamali");
    }

    @RestController
    static class FailingController {

        @PostMapping("/test/anonymous")
        void anonymous() {
            throw new InsufficientAuthenticationException("Full authentication is required to access this resource");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Access Denied");
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResourceNotFoundException("{user.not.found}");
        }
    }
}
