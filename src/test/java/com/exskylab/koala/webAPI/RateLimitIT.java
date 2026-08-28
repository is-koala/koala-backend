package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.max-attempts=3",
        "rate-limit.window=1m"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAttemptsBeyondTheLimitAreRejectedWithTooManyRequests() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(loginRequest()).andExpect(status().is(not429()));
        }

        mockMvc.perform(loginRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void anUnlimitedEndpointIsNotAffected() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            mockMvc.perform(post("/boyle-bir-uc-yok"))
                    .andExpect(status().is(not429()));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest() {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"yok@example.com\",\"password\":\"Parola123\"}");
    }

    private org.hamcrest.Matcher<Integer> not429() {
        return org.hamcrest.Matchers.not(429);
    }
}
