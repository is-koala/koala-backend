package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PricingQuoteIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anEmployerCanRequestTheBackendPriceBeforePublishingAJob() throws Exception {
        mockMvc.perform(post("/payment-quotes")
                        .with(user("employer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerPayout\":\"1000.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingVersion").value("2026-09-v1"))
                .andExpect(jsonPath("$.data.currency").value("TRY"))
                .andExpect(jsonPath("$.data.workerPayout").value("1000.00"))
                .andExpect(jsonPath("$.data.serviceFeeRate").value("0.075"))
                .andExpect(jsonPath("$.data.serviceFeeAmount").value("75.00"))
                .andExpect(jsonPath("$.data.employerTotal").value("1075.00"));
    }

    @Test
    void anInvalidPriceDoesNotProduceAnInventedQuote() throws Exception {
        mockMvc.perform(post("/payment-quotes")
                        .with(user("employer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerPayout\":\"0\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pricingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/payment-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerPayout\":\"1000.00\"}"))
                .andExpect(status().isUnauthorized());
    }
}
