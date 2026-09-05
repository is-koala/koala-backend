package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class QaAssetsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void qaMediaIsPubliclyReadableByTheMobileClient() throws Exception {
        mockMvc.perform(get("/qa-assets/companies/sirket-00.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        mockMvc.perform(get("/qa-assets/cv/ornek-cv.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }
}
