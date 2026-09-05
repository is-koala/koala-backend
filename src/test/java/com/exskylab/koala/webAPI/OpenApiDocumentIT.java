package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "SPRINGDOC_ENABLED=true")
class OpenApiDocumentIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDocumentBuildsAndDescribesEveryPath() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Koala API"))
                .andExpect(jsonPath("$.paths.length()").value(greaterThan(100)));
    }

    @Test
    void theBearerSchemeIsDeclaredAndAppliedGlobally() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/users/me'].get.security[0].bearerAuth").exists());
    }

    @Test
    void aProtectedEndpointDocumentsItsAuthorisationFailures() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/users/me'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/users/me'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/users/me'].get.summary").exists());
    }

    @Test
    void aPublicEndpointIsNotShownAsRequiringAToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/jobs'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/jobs'].get.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/jobs'].get.responses.403").doesNotExist());
    }

    @Test
    void theErrorEnvelopeIsPartOfTheSchemaCatalogue() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCategory").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCode").exists());
    }

    @Test
    void addressWritesExposeOnlyTheCanonicalV2Contract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.countryId").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.cityId").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.districtId").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.components").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.position").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.candidateToken").exists())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.countryCode").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.cityName").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.districtName").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressWriteV2.properties.addressLine").doesNotExist());
    }

    @Test
    void addressSearchDocumentsTheOpaqueSessionAndCandidateFlow() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/locations/address-search-sessions'].post").exists())
                .andExpect(jsonPath("$.paths['/locations/address-search-sessions/{sessionId}/suggestions'].get").exists())
                .andExpect(jsonPath("$.paths['/locations/address-search-sessions/{sessionId}/candidates/{candidateId}/resolve'].post").exists())
                .andExpect(jsonPath("$.paths['/locations/address-candidates/reverse'].post").exists())
                .andExpect(jsonPath("$.components.schemas.AddressCandidateDto.properties.candidateToken").exists())
                .andExpect(jsonPath("$.components.schemas.AddressSuggestionDto.properties.placeId").doesNotExist());
    }
}
