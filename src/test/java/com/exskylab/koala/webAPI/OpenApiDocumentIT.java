package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorCategory;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "SPRINGDOC_ENABLED=true")
class OpenApiDocumentIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void jobSearchDocumentsTheStartRangeAndDistanceParametersButNotTheValidationGetters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'startFrom')].description")
                        .value(hasItem(containsString("Europe/Istanbul"))))
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'startTo')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'lat')].description")
                        .value(hasItem(containsString("distanceKm"))))
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'lng')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'startRangeOrdered')]").isEmpty())
                .andExpect(jsonPath("$.paths['/jobs'].get.parameters[?(@.name == 'locationComplete')]").isEmpty())
                .andExpect(jsonPath("$.paths['/jobs'].get.description").value(containsString("sort=distance,asc")))
                .andExpect(jsonPath("$.components.schemas.JobSummaryDto.properties.distanceKm.description")
                        .value(containsString("km")));
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
    void assignmentTimelineAndEmployerIdentityAreDocumentedInTurkish() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/jobs/{jobId}/assignments'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/jobs/{jobId}/assignments'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/jobs/{jobId}/assignments'].get.security").exists())
                .andExpect(jsonPath("$.paths['/job-assignments/{assignmentId}/respond'].post.responses.200.content"
                        + "['*/*'].schema.$ref").value(containsString("MyAssignmentDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.confirmedAt.type")
                        .value(hasItems("string", "null")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.confirmedAt.description")
                        .value(containsString("kabul")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.paymentSecuredAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.application.$ref")
                        .value(containsString("AssignmentApplicationDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.employer.$ref")
                        .value(containsString("SafeUserDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.company.$ref")
                        .value(containsString("CompanySummaryDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.cancellation.$ref")
                        .value(containsString("AssignmentCancellationDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.payout.$ref")
                        .value(containsString("AssignmentPayoutDto")))
                .andExpect(jsonPath("$.components.schemas.AssignmentCancellationDto.properties.feeRate.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.JobSummaryDto.properties.companyLogoUrl.description")
                        .value(containsString("logo")))
                .andExpect(jsonPath("$.components.schemas.JobApplicantDto.properties.answeredDateTime.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.JobApplicantDto.properties.assignmentId.format")
                        .value("uuid"))
                .andExpect(jsonPath("$.components.schemas.JobApplicantDto.properties.rejectionReason").exists())
                .andExpect(jsonPath("$.components.schemas.PayoutDto.properties.jobTitle").exists())
                .andExpect(jsonPath("$.components.schemas.PayoutDto.properties.companyName").exists())
                .andExpect(jsonPath("$.components.schemas.JobDetailDto.properties.applicationCount").exists())
                .andExpect(jsonPath("$.components.schemas.JobDetailDto.properties.cancelledAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.JobDetailDto.properties.updatedAt").exists())
                .andExpect(jsonPath("$.components.schemas.PublicProfileDto.properties.completedJobCount").exists())
                .andExpect(jsonPath("$.components.schemas.PublicProfileDto.properties.averageRating").exists())
                .andExpect(jsonPath("$.components.schemas.PublicProfileDto.properties.reviewCount").exists())
                .andExpect(jsonPath("$.components.schemas.PublicProfileDto.properties.badges.items.$ref")
                        .value(containsString("BadgeDto")));
    }

    @Test
    void assignmentReviewStateAndViewerSidedPricingAreDocumentedInTurkish() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.myReview.$ref")
                        .value(containsString("MyReviewDto")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.counterpartyReviewed.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.counterpartyReviewed.description")
                        .value(containsString("Karşı taraf")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.totalCharged.type")
                        .value(hasItems("number", "null")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.totalCharged.description")
                        .value(containsString("işçiyse null")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.pricing.description")
                        .value(containsString("işçiyse null")))
                .andExpect(jsonPath("$.components.schemas.MyAssignmentDto.properties.workerPayout.description")
                        .value(containsString("kazanc")))
                .andExpect(jsonPath("$.components.schemas.MyReviewDto.properties.rating.description")
                        .value(containsString("1-5")))
                .andExpect(jsonPath("$.components.schemas.ReviewDto.properties.jobAssignmentId.type")
                        .value(hasItems("string", "null")))
                .andExpect(jsonPath("$.components.schemas.ReviewDto.properties.jobAssignmentId.format")
                        .value("uuid"))
                .andExpect(jsonPath("$.components.schemas.ReviewDto.properties.jobAssignmentId.description")
                        .value(containsString("POST")));
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
    void conversationParticipantsDocumentTheirRoleReadStampAndDeparture() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ConversationDto.properties.participants.items.$ref")
                        .value("#/components/schemas/ConversationParticipantDto"))
                .andExpect(jsonPath("$.components.schemas.ConversationParticipantDto.properties.role.enum")
                        .value(containsInAnyOrder("EMPLOYER", "COMPANY_CONTACT", "WORKER")))
                .andExpect(jsonPath("$.components.schemas.ConversationParticipantDto.properties.lastReadAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.ConversationParticipantDto.properties.joinedAt").exists())
                .andExpect(jsonPath("$.components.schemas.ConversationParticipantDto.properties.left.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.MessageDto.properties.senderName").exists())
                .andExpect(jsonPath("$.components.schemas.MessageDto.properties.senderProfilePictureUrl").exists())
                .andExpect(jsonPath("$.components.schemas.MessageDto.properties.readBy").doesNotExist());
    }

    @Test
    void theUnsubscribeEndpointDocumentsItsAnswerAndBothTokenRefusals() throws Exception {
        String endpoint = "$.paths['/notification-preferences/unsubscribe'].post";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(endpoint + ".security").doesNotExist())
                .andExpect(jsonPath(endpoint + ".responses.200.content['*/*'].schema.$ref")
                        .value("#/components/schemas/SuccessDataResultUnsubscribedChannelDto"))
                .andExpect(jsonPath(endpoint + ".responses.400.description")
                        .value(containsString("NOTIFICATION_UNSUBSCRIBE_TOKEN_INVALID")))
                .andExpect(jsonPath(endpoint + ".responses.400.description")
                        .value(containsString("NOTIFICATION_UNSUBSCRIBE_TOKEN_EXPIRED")))
                .andExpect(jsonPath(endpoint + ".responses.400.content['*/*'].schema.$ref")
                        .value("#/components/schemas/ErrorResult"))
                .andExpect(jsonPath(endpoint + ".responses.401").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SuccessDataResultUnsubscribedChannelDto.properties.data.$ref")
                        .value("#/components/schemas/UnsubscribedChannelDto"))
                .andExpect(jsonPath("$.components.schemas.UnsubscribedChannelDto.properties.category.enum")
                        .value(hasItems("NEWSLETTER", "MARKETINGS", "JOB_ALERTS")))
                .andExpect(jsonPath("$.components.schemas.UnsubscribedChannelDto.properties.channel.enum")
                        .value(hasItems("EMAIL", "PUSH")))
                .andExpect(jsonPath("$.components.schemas.UnsubscribedChannelDto.properties.alreadyDisabled.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.UnsubscribedChannelDto.properties.length()").value(3));
    }

    @Test
    void theErrorEnvelopeIsPartOfTheSchemaCatalogue() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCategory").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResult.properties.errorCode").exists());
    }

    @Test
    void theErrorCategorySchemaListsEveryJavaConstant() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Set<String> published = publishedErrorCategories(document);
        Set<String> javaConstants = Arrays.stream(ErrorCategory.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(javaConstants, published);
    }

    private static Set<String> publishedErrorCategories(JsonNode document) {
        JsonNode property = document.path("components").path("schemas")
                .path("ErrorResult").path("properties").path("errorCategory");
        Set<String> fromProperty = enumValues(document, property);
        if (!fromProperty.isEmpty()) {
            return fromProperty;
        }
        return enumValues(document, document.path("components").path("schemas").path("ErrorCategory"));
    }

    private static Set<String> enumValues(JsonNode document, JsonNode schema) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return Set.of();
        }
        if (schema.has("enum") && schema.get("enum").isArray()) {
            Set<String> values = new LinkedHashSet<>();
            for (JsonNode value : schema.get("enum")) {
                if (value.isTextual()) {
                    values.add(value.asText());
                }
            }
            return values;
        }
        if (schema.has("$ref") && schema.get("$ref").isTextual()) {
            return enumValues(document, resolveRef(document, schema.get("$ref").asText()));
        }
        for (String combinator : List.of("oneOf", "anyOf", "allOf")) {
            JsonNode options = schema.get(combinator);
            if (options == null || !options.isArray()) {
                continue;
            }
            Set<String> combined = new LinkedHashSet<>();
            for (JsonNode option : options) {
                combined.addAll(enumValues(document, option));
            }
            if (!combined.isEmpty()) {
                return combined;
            }
        }
        return Set.of();
    }

    private static JsonNode resolveRef(JsonNode document, String ref) {
        if (!ref.startsWith("#/")) {
            return document.path("__missing");
        }
        JsonNode current = document;
        for (String segment : ref.substring(2).split("/")) {
            current = current.path(segment);
        }
        return current;
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
    void multipartEndpointsDocumentTheirTransportLimitFailure() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/images'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/users/me/cv'].put.responses.413").exists())
                .andExpect(jsonPath("$.paths['/users/me/profile-picture'].put.responses.413").exists())
                .andExpect(jsonPath("$.paths['/users/me/identity-documents'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/companies'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/companies/{companyId}/logo'].put.responses.413").exists())
                .andExpect(jsonPath("$.paths['/companies/{companyId}/images'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/jobs/{jobId}/images'].post.responses.413").exists())
                .andExpect(jsonPath("$.paths['/admin/badges/{badgeId}/icon'].put.responses.413").exists())
                .andExpect(jsonPath("$.paths['/disputes/{disputeId}/evidence'].post.responses.413").exists());
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
                .andExpect(jsonPath("$.components.schemas.AddressCandidateDto.properties.precision.enum")
                        .value(contains("BUILDING", "STREET", "NEIGHBORHOOD")))
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
