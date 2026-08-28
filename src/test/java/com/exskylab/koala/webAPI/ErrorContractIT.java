package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ErrorContractIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void refreshWithoutAnAuthorizationHeaderIsUnauthorizedNotAServerError() throws Exception {
        mockMvc.perform(post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"herhangi-bir-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithAMalformedAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh-token")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"herhangi-bir-token\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh-token")
                        .header("Authorization", "Basic abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"herhangi-bir-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserReachingAnAdminEndpointIsForbiddenNotAServerError() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "a.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/images").file(image)
                        .with(user("worker").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshGetsPastAnExpiredAccessTokenAndFailsOnTheSessionInstead() throws Exception {
        String expired = io.jsonwebtoken.Jwts.builder()
                .subject(java.util.UUID.randomUUID().toString())
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 90_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(post("/auth/refresh-token")
                        .header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"herhangi-bir-token\"}"))
                .andExpect(jsonPath("$.errorCode").value(
                        org.hamcrest.Matchers.not("JWT_EXPIRED")));
    }

    @Test
    void jobSearchIsPublicAndPaginated() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void sortingByANonWhitelistedFieldIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/jobs").param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"));
    }

    @Test
    void referenceDataIsPublic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/reference/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/reference/job-categories"))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownPathIsNotFoundNotAServerError() throws Exception {
        mockMvc.perform(post("/images/")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/boyle-bir-uc-yok")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousRequestToAProtectedEndpointIsUnauthorized() throws Exception {
        mockMvc.perform(post("/users/me/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonIsRejectedWithBadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/auth/start-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bozuk json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInvalidBodyIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(post("/auth/start-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0].field").exists())
                .andExpect(jsonPath("$.details[0].message").exists());
    }

    @Test
    void noResponseLeaksARawMessageKey() throws Exception {
        mockMvc.perform(post("/auth/start-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.startsWith("{"))))
                .andExpect(jsonPath("$.details[0].message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.startsWith("{"))));
    }

    @Test
    void messagesFollowTheAcceptLanguageHeader() throws Exception {
        mockMvc.perform(post("/auth/start-registration")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.message").value("Some of the details you sent are not valid."));

        mockMvc.perform(post("/auth/start-registration")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.message").value("Gönderdiğiniz bilgilerde hata var."));
    }

    @Test
    void anUnexpectedFailureDoesNotEchoInternalDetail() throws Exception {
        mockMvc.perform(post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));
    }
}
