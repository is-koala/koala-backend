package com.exskylab.koala.core.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class RequestIdIT extends AbstractIntegrationTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RETIRED_HEADER = "X-Correlation-Id";
    private static final String CLIENT_VALUE = "istemcinin-uydurdugu-deger";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theEnvelopeCarriesAServerGeneratedRequestId() throws Exception {
        MvcResult result = mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        assertEquals(jsonField(result, "requestId"), result.getResponse().getHeader(REQUEST_ID_HEADER));
    }

    @Test
    void theRequestIdHasExactlyOneNameInTheEnvelopeAndOneInTheHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.correlationId").doesNotExist())
                .andReturn();

        assertNull(result.getResponse().getHeader(RETIRED_HEADER),
                "Yanit ikinci bir isimle ayni degeri tasiyor, tek isim X-Request-Id olmali");
    }

    @Test
    void aClientSuppliedIdNeverBecomesTheRequestId() throws Exception {
        MvcResult withRequestId = mockMvc.perform(post("/payments/callback")
                        .param("token", "bilinmeyen-token")
                        .header(REQUEST_ID_HEADER, CLIENT_VALUE))
                .andReturn();

        assertNotEquals(CLIENT_VALUE, jsonField(withRequestId, "requestId"),
                "requestId sunucu tarafindan uretilmeli, istemci basligindan gelmemeli");

        MvcResult withRetiredHeader = mockMvc.perform(post("/payments/callback")
                        .param("token", "bilinmeyen-token")
                        .header(RETIRED_HEADER, CLIENT_VALUE))
                .andReturn();

        assertNotEquals(CLIENT_VALUE, jsonField(withRetiredHeader, "requestId"),
                "requestId sunucu tarafindan uretilmeli, istemci basligindan gelmemeli");
    }

    @Test
    void theRequestIdAndTheRequestFactsReachTheMdcOfTheAccessLog() throws Exception {
        Logger accessLog = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        accessLog.addAppender(appender);

        try {
            MvcResult result = mockMvc.perform(post("/payments/callback")
                            .param("token", "bilinmeyen-token")
                            .header(RETIRED_HEADER, CLIENT_VALUE)
                            .header(REQUEST_ID_HEADER, CLIENT_VALUE))
                    .andReturn();

            String requestId = jsonField(result, "requestId");
            ILoggingEvent event = appender.list.stream()
                    .filter(candidate -> requestId.equals(candidate.getMDCPropertyMap().get("requestId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "http.access logger'inda requestId tasiyan satir yok: " + appender.list));

            Map<String, String> context = event.getMDCPropertyMap();
            assertEquals("POST", context.get("method"));
            assertEquals("/payments/callback", context.get("path"));
            assertEquals("404", context.get("status"));
            assertNotNull(context.get("durationMs"));
            assertNotNull(context.get("clientIp"));
            assertFalse(context.containsValue(CLIENT_VALUE),
                    "Istemcinin gonderdigi id hicbir MDC alanina yazilmamali: " + context);
        } finally {
            accessLog.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void theMdcIsEmptiedWhenTheRequestEnds() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"));

        assertNull(MDC.get("requestId"), "Istek bittikten sonra MDC bosalmali");
        assertNull(MDC.get("path"), "Istek bittikten sonra MDC bosalmali");
    }

    private String jsonField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), field + " yanit zarfinda yok: " + body);
        return matcher.group(1);
    }
}
