package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentCallbackIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theCallbackIsReachableWithoutAuthenticationBecauseTheProviderCallsIt() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void aCallbackWithoutATokenIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/payments/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingPaymentAttemptsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/job-assignments/" + java.util.UUID.randomUUID() + "/payments"))
                .andExpect(status().isUnauthorized());
    }
}
