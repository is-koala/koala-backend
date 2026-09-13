package com.exskylab.koala.webAPI;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.configs.RequestLoggingFilter;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ErrorEnvelopeIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anErrorCarriesBothASwitchableCategoryAndASpecificCode() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCategory").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/payments/callback"))
                .andExpect(jsonPath("$.timeStamp").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void theCategoryIsAClosedSetTheClientCanSwitchOnWhileTheCodeStaysSpecific() throws Exception {
        mockMvc.perform(post("/payments/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("PARAMETER_MISSING"));
    }

    @Test
    void anErrorNamesTheResourceItWasAbout() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(jsonPath("$.resource").value("callback"));
    }

    @Test
    void theMessageFollowsTheAcceptLanguageHeader() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "yok").header("Accept-Language", "en"))
                .andExpect(jsonPath("$.message").value("Payment record not found."));

        mockMvc.perform(post("/payments/callback").param("token", "yok").header("Accept-Language", "tr"))
                .andExpect(jsonPath("$.message").value("Ödeme kaydı bulunamadı."));
    }

    @Test
    void anUnknownLanguageFallsBackInsteadOfReturningTheRawKey() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "yok").header("Accept-Language", "de"))
                .andExpect(jsonPath("$.message").value(not(startsWith("{"))));
    }

    @Test
    void anUnknownEndpointIsUnauthorisedSoItDoesNotLeakWhichEndpointsExist() throws Exception {
        mockMvc.perform(get("/boyle-bir-uc-yok"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCategory").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anAnonymousPostToTheRootThatOnlyAcceptsHtmlStillGetsTheJsonEnvelopeAndASingleWarning() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        String threadName = Thread.currentThread().getName();

        try {
            mockMvc.perform(post("/").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.httpStatus").value("401 UNAUTHORIZED"))
                    .andExpect(jsonPath("$.errorCategory").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(jsonPath("$.message").value(not(startsWith("{"))))
                    .andExpect(jsonPath("$.requestId").exists());

            List<ILoggingEvent> onThisThread = appender.list.stream()
                    .filter(event -> threadName.equals(event.getThreadName()))
                    .toList();
            List<ILoggingEvent> withStackTrace = onThisThread.stream()
                    .filter(event -> event.getThrowableProxy() != null)
                    .toList();
            assertTrue(withStackTrace.isEmpty(), "Anonim istek yigin izi loglamamali: " + withStackTrace);

            List<ILoggingEvent> warnings = onThisThread.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                    .toList();
            assertEquals(1, warnings.size(), "Anonim istek tek WARN satiri birakmali: " + warnings);
            assertEquals(RequestLoggingFilter.ACCESS_LOGGER_NAME, warnings.getFirst().getLoggerName());
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void aProtectedEndpointAnsweredToAClientThatOnlyAcceptsHtmlIsStillTheJsonEnvelope() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCategory").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/users/me"));
    }

    @Test
    void anUnreadableBodyIsAMalformedRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/auth/login").contentType("application/json").content("{bozuk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("REQUEST_MALFORMED"));
    }

    @Test
    void aValidationFailureListsTheOffendingFieldsWithResolvedMessages() throws Exception {
        mockMvc.perform(post("/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0].field").exists())
                .andExpect(jsonPath("$.details[0].message").value(not(startsWith("{"))));
    }
}
