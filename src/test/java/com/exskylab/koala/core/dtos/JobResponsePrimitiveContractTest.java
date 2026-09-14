package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobResponsePrimitiveContractTest {

    private static final Path JOB_RESPONSE =
            Path.of("src/main/java/com/exskylab/koala/core/dtos/job/response");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> WRAPPED_FLAGS = Set.of(
            "JobDetailDto.insuranceRequired",
            "JobDetailDto.experienceRequired",
            "JobDetailDto.minAge",
            "JobDetailDto.maxAge",
            "JobSummaryDto.insuranceRequired",
            "JobSummaryDto.experienceRequired",
            "CompanyJobDto.insuranceRequired",
            "CompanyJobDto.experienceRequired",
            "CompanyJobDto.minAge",
            "CompanyJobDto.maxAge",
            "IndividualJobDto.insuranceRequired",
            "IndividualJobDto.experienceRequired",
            "IndividualJobDto.minAge",
            "IndividualJobDto.maxAge");

    private static final Set<String> DOCUMENTED_PRIMITIVE_DEFAULTS = Set.of(
            "JobDetailDto.acceptingApplications",
            "JobDetailDto.applicationCount",
            "JobDetailDto.hiredCount",
            "JobSummaryDto.applicationCount",
            "JobSummaryDto.hiredCount",
            "CompanyJobDto.addressPublic");

    @Test
    void theJobFlagFieldsAreWrappersNotPrimitives() throws NoSuchFieldException {
        assertEquals(Boolean.class, fieldType(JobDetailDto.class, "insuranceRequired"));
        assertEquals(Boolean.class, fieldType(JobDetailDto.class, "experienceRequired"));
        assertEquals(Integer.class, fieldType(JobDetailDto.class, "minAge"));
        assertEquals(Integer.class, fieldType(JobDetailDto.class, "maxAge"));

        assertEquals(Boolean.class, fieldType(JobSummaryDto.class, "insuranceRequired"));
        assertEquals(Boolean.class, fieldType(JobSummaryDto.class, "experienceRequired"));

        assertEquals(Boolean.class, fieldType(CompanyJobDto.class, "insuranceRequired"));
        assertEquals(Boolean.class, fieldType(CompanyJobDto.class, "experienceRequired"));
        assertEquals(Integer.class, fieldType(CompanyJobDto.class, "minAge"));
        assertEquals(Integer.class, fieldType(CompanyJobDto.class, "maxAge"));

        assertEquals(Boolean.class, fieldType(IndividualJobDto.class, "insuranceRequired"));
        assertEquals(Boolean.class, fieldType(IndividualJobDto.class, "experienceRequired"));
        assertEquals(Integer.class, fieldType(IndividualJobDto.class, "minAge"));
        assertEquals(Integer.class, fieldType(IndividualJobDto.class, "maxAge"));
    }

    @Test
    void remainingPrimitivesOnThoseDtosAreTheDocumentedOnes() throws IOException {
        Set<String> found = new TreeSet<>();
        for (String name : Set.of("JobDetailDto", "JobSummaryDto", "CompanyJobDto", "IndividualJobDto")) {
            Path file = JOB_RESPONSE.resolve(name + ".java");
            found.addAll(RequestDtoPrimitiveContractTest.primitiveFieldsIn(
                    name, Files.readString(file, StandardCharsets.UTF_8)));
        }

        assertTrue(WRAPPED_FLAGS.stream().noneMatch(found::contains),
                "Job flag still primitive after #183: "
                        + WRAPPED_FLAGS.stream().filter(found::contains).toList());
        assertEquals(DOCUMENTED_PRIMITIVE_DEFAULTS, found);
    }

    @Test
    void theFlagsKeepTheirJsonNamesWhenSerialized() {
        var detail = new JobDetailDto();
        detail.setInsuranceRequired(Boolean.TRUE);
        detail.setExperienceRequired(Boolean.FALSE);
        detail.setMinAge(18);
        detail.setMaxAge(65);

        String json = JSON.writeValueAsString(detail);
        assertTrue(json.contains("\"insuranceRequired\":true"));
        assertTrue(json.contains("\"experienceRequired\":false"));
        assertTrue(json.contains("\"minAge\":18"));
        assertTrue(json.contains("\"maxAge\":65"));
    }

    private static Class<?> fieldType(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        return field.getType();
    }
}
