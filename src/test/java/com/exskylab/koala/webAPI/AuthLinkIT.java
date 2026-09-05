package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthLinkIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetLandingIsPublicNonCacheableAndOpensOnlyTheConfiguredApp() throws Exception {
        mockMvc.perform(get("/auth-links/reset-password").param("token", "reset-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(content().string(containsString(
                        "koala-staging://reset-password?token=reset-token")));
    }

    @Test
    void registrationLandingUsesTheRegistrationDestination() throws Exception {
        mockMvc.perform(get("/auth-links/complete-registration").param("token", "registration-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "koala-staging://complete-registration?token=registration-token")));
    }

    @Test
    void landingRejectsDuplicateAndMissingTokens() throws Exception {
        mockMvc.perform(get("/auth-links/reset-password")
                        .param("token", "one", "two"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/auth-links/reset-password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenIsHtmlEscapedInsteadOfBecomingMarkup() throws Exception {
        mockMvc.perform(get("/auth-links/reset-password")
                        .param("token", "\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
