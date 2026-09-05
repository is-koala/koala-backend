package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthLinkIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetLegacyLinkPermanentlyRedirectsToTheWebWithoutLeakingReferrerData() throws Exception {
        mockMvc.perform(get("/auth-links/reset-password").param("token", "reset-token"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location",
                        "http://localhost:3000/sifre-sifirla?token=reset-token"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")));
    }

    @Test
    void registrationLegacyLinkRedirectsToTheTurkishWebRoute() throws Exception {
        mockMvc.perform(get("/auth-links/complete-registration").param("token", "registration-token"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location",
                        "http://localhost:3000/hesap-dogrula?token=registration-token"));
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
    void tokenIsEncodedInsteadOfBecomingRedirectSyntax() throws Exception {
        mockMvc.perform(get("/auth-links/reset-password")
                        .param("token", "\"><script>alert(1)</script>"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", not(containsString("<script>"))))
                .andExpect(header().string("Location", containsString("%3Cscript%3E")));
    }
}
