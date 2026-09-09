package com.exskylab.koala.core.exceptions;

import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.monitoring.BackendErrorMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMonitoringTest {

    @Test
    void missingTrustAnchorsKeepThePublic400ContractAndReachOperationalMonitoring() throws Exception {
        RecordingBackendErrorMonitor monitor = new RecordingBackendErrorMonitor();
        MessageResolver messageResolver = new MessageResolver(new StaticMessageSource());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IdentityFailureController())
                .setControllerAdvice(new GlobalExceptionHandler(messageResolver, monitor))
                .build();

        mockMvc.perform(get("/test/identity/no-trust-anchors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("400 BAD_REQUEST"))
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_NO_TRUST_ANCHORS"));

        assertEquals(1, monitor.captureCount);
        assertEquals("IDENTITY_NO_TRUST_ANCHORS", monitor.code);
        assertInstanceOf(ValidationException.class, monitor.error);
    }

    @Test
    void ordinaryValidationFailuresDoNotReachOperationalMonitoring() throws Exception {
        RecordingBackendErrorMonitor monitor = new RecordingBackendErrorMonitor();
        MessageResolver messageResolver = new MessageResolver(new StaticMessageSource());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IdentityFailureController())
                .setControllerAdvice(new GlobalExceptionHandler(messageResolver, monitor))
                .build();

        mockMvc.perform(get("/test/identity/validation-failure"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertEquals(0, monitor.captureCount);
    }

    @RestController
    static class IdentityFailureController {

        @GetMapping("/test/identity/no-trust-anchors")
        void failBecauseTrustAnchorsAreMissing() {
            throw new ValidationException(IdentityMessages.NO_TRUST_ANCHORS);
        }

        @GetMapping("/test/identity/validation-failure")
        void failValidation() {
            throw new ValidationException("{validation.failed}");
        }
    }

    static class RecordingBackendErrorMonitor implements BackendErrorMonitor {

        private int captureCount;
        private Throwable error;
        private String code;

        @Override
        public void capture(Throwable error, String code) {
            captureCount++;
            this.error = error;
            this.code = code;
        }
    }
}
