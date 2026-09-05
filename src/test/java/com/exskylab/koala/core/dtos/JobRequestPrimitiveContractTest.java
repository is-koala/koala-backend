package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JobRequestPrimitiveContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Validator validator;

    JobRequestPrimitiveContractTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<String> violatedFields(Object dto) {
        return validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void anIndividualJobPostingMissingItsFlagsIsRejectedNotDefaulted() {
        var dto = JSON.readValue("{}", CreateIndividualJobDto.class);

        assertNull(dto.getExperienceRequired(), "eksik alan sessizce false olmamalı");
        assertTrue(violatedFields(dto).containsAll(Set.of("experienceRequired", "minAge", "maxAge")));
    }

    @Test
    void aCompanyJobPostingMissingItsFlagsIsRejectedNotDefaulted() {
        var dto = JSON.readValue("{}", CreateCompanyJobDto.class);

        assertNull(dto.getExperienceRequired());
        assertNull(dto.getInsuranceRequired());
        assertFalse(dto.isAddressPublic(), "addressPublic omitted from legacy clients must default to false");
        assertTrue(violatedFields(dto).containsAll(
                Set.of("experienceRequired", "insuranceRequired", "minAge", "maxAge")));
    }

    @Test
    void theFlagsRoundTripUnderTheirOwnJsonNames() {
        var dto = JSON.readValue("""
                {"experienceRequired": true, "insuranceRequired": true, "minAge": 18, "maxAge": 65}
                """, CreateCompanyJobDto.class);

        assertEquals(Boolean.TRUE, dto.getExperienceRequired());
        assertEquals(Boolean.TRUE, dto.getInsuranceRequired());
        assertEquals(18, dto.getMinAge());
        assertEquals(65, dto.getMaxAge());

        assertTrue(JSON.writeValueAsString(dto).contains("\"experienceRequired\":true"));
        assertTrue(JSON.writeValueAsString(dto).contains("\"insuranceRequired\":true"));
    }

    @Test
    void anAgeRangeBelowOneIsRejected() {
        var dto = JSON.readValue("""
                {"experienceRequired": false, "insuranceRequired": false, "minAge": 0, "maxAge": 0}
                """, CreateCompanyJobDto.class);

        assertTrue(violatedFields(dto).containsAll(Set.of("minAge", "maxAge")));
    }
}
