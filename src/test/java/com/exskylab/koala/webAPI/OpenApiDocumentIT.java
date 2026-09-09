package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
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
    void companyJobSearchAndAssignmentReadAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'companyId')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/job-assignments/{assignmentId}'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/job-assignments/{assignmentId}'].get.responses.404").exists());
    }

    @Test
    void jobSummariesDocumentTheirAuthoritativeRevisionTime() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.JobSummaryDto.properties.updatedAt.type")
                        .value(hasItems("string", "null")))
                .andExpect(jsonPath("$.components.schemas.JobSummaryDto.properties.updatedAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.JobSummaryDto.properties.updatedAt.description")
                        .value(containsString("sitemap")));
    }

    @Test
    void sitemapProjectionDocumentsItsPublicPrivacySafeContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifest'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifest'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifest'].get.responses.429").exists())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifests/{version}/shards/{shard}'].get.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifests/{version}/shards/{shard}'].get.responses.400")
                        .exists())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifests/{version}/shards/{shard}'].get.responses.404")
                        .exists())
                .andExpect(jsonPath("$.paths['/sitemap/jobs/manifests/{version}/shards/{shard}'].get.responses.429")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SitemapJobDto.properties.length()").value(2))
                .andExpect(jsonPath("$.components.schemas.SitemapJobDto.properties.updatedAt.type")
                        .value(hasItems("string", "null")))
                .andExpect(jsonPath("$.components.schemas.SitemapJobDto.properties.updatedAt.format")
                        .value("date-time"));
    }

    @Test
    void theErrorEnvelopeIsPartOfTheSchemaCatalogue() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCategory").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCode").exists());
    }

    @Test
    void authEndpointsDocumentTheirSpecificFailures() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/auth/start-registration'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/start-registration'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/start-registration'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/start-registration'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/verify-registration-token'].post.responses.404").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/complete-registration'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.404").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh-token'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/forgot-password'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/forgot-password'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/forgot-password'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/forgot-password'].post.responses.426").exists())
                .andExpect(jsonPath("$.paths['/auth/reset-password'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/auth/reset-password'].post.responses.429").exists())
                .andExpect(jsonPath("$.paths['/auth/reset-password'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/auth/reset-password'].post.responses.426").exists());
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

    @Test
    void timesheetDocumentsOfflineEventIdentityAndAuditFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.CheckPointDto.properties.clientEventId").exists())
                .andExpect(jsonPath("$.components.schemas.CheckPointDto.properties.capturedAt").exists())
                .andExpect(jsonPath("$.components.schemas.WorkSessionDto.properties.checkInReceivedAt").exists())
                .andExpect(jsonPath("$.components.schemas.WorkSessionDto.properties.checkInPlausibilityFlags").exists())
                .andExpect(jsonPath("$.components.schemas.WorkSessionDto.properties.checkOutReceivedAt").exists())
                .andExpect(jsonPath("$.components.schemas.WorkSessionDto.properties.checkOutPlausibilityFlags").exists());
    }
}
