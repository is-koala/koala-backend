package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.correlationId").exists());
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
