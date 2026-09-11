package com.exskylab.koala.core.dtos.company;

import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.entities.Company;
import jakarta.persistence.Column;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyFieldWidthRulesTest {

    private static final int WEBSITE_MAX = 512;
    private static final int DESCRIPTION_MAX = 4000;

    private final Validator validator;

    CompanyFieldWidthRulesTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void updateDtoWebsiteAndDescriptionMatchThePromisedWidths() {
        UpdateCompanyRequestDto dto = new UpdateCompanyRequestDto();

        dto.setWebsite("w".repeat(WEBSITE_MAX));
        dto.setDescription("d".repeat(DESCRIPTION_MAX));
        assertTrue(validator.validate(dto).isEmpty());

        dto.setWebsite("w".repeat(WEBSITE_MAX + 1));
        assertFalse(validator.validate(dto).isEmpty());

        dto.setWebsite("w".repeat(WEBSITE_MAX));
        dto.setDescription("d".repeat(DESCRIPTION_MAX + 1));
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test
    void createDtoWebsiteAndDescriptionAreCappedTheSameWay() {
        CreateCompanyRequestDto dto = new CreateCompanyRequestDto();
        dto.setWebsite("w".repeat(WEBSITE_MAX));
        dto.setDescription("d".repeat(DESCRIPTION_MAX));
        assertTrue(websiteAndDescriptionValid(dto));

        dto.setWebsite("w".repeat(WEBSITE_MAX + 1));
        assertFalse(websiteAndDescriptionValid(dto));

        dto.setWebsite("w".repeat(WEBSITE_MAX));
        dto.setDescription("d".repeat(DESCRIPTION_MAX + 1));
        assertFalse(websiteAndDescriptionValid(dto));
    }

    @Test
    void companyColumnsAreAsWideAsTheDtoPromise() throws Exception {
        assertEquals(WEBSITE_MAX, columnLength("website"));
        assertEquals(DESCRIPTION_MAX, columnLength("description"));
    }

    private boolean websiteAndDescriptionValid(CreateCompanyRequestDto dto) {
        return validator.validateProperty(dto, "website").isEmpty()
                && validator.validateProperty(dto, "description").isEmpty();
    }

    private static int columnLength(String fieldName) throws Exception {
        Field field = Company.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        return column.length();
    }
}
