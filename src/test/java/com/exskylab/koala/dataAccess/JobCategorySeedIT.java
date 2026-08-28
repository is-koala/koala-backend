package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobCategory;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JobCategorySeedIT extends AbstractIntegrationTest {

    private static final Map<String, String> EXPECTED_IDS = Map.of(
            "PHOTOGRAPHY", "9a1c0f10-0001-4c00-8000-000000000001",
            "WAITER",      "9a1c0f10-0001-4c00-8000-000000000002",
            "PET_CARE",    "9a1c0f10-0001-4c00-8000-000000000003",
            "DESIGN",      "9a1c0f10-0001-4c00-8000-000000000004",
            "BARISTA",     "9a1c0f10-0001-4c00-8000-000000000005");

    @Autowired
    private JobCategoryDao jobCategoryDao;

    @Test
    void theFiveCategoriesTheMobileAppMapsIconsToAreSeeded() {
        Map<String, JobCategory> byCode = jobCategoryDao.findAll().stream()
                .collect(Collectors.toMap(JobCategory::getCode, Function.identity()));

        assertTrue(byCode.keySet().containsAll(EXPECTED_IDS.keySet()),
                "seed eksik, bulunan kodlar: " + byCode.keySet());
    }

    @Test
    void theIdsAreFixedSoTheyMatchAcrossEnvironments() {
        Map<String, JobCategory> byCode = jobCategoryDao.findAll().stream()
                .collect(Collectors.toMap(JobCategory::getCode, Function.identity()));

        EXPECTED_IDS.forEach((code, id) ->
                assertEquals(UUID.fromString(id), byCode.get(code).getId(), "kategori " + code));
    }

    @Test
    void everyCodeIsAsciiAndIndependentOfTheDisplayName() {
        jobCategoryDao.findAll().forEach(category -> {
            assertNotNull(category.getCode(), "kategori " + category.getName() + " kodsuz");
            assertTrue(category.getCode().matches("[A-Z][A-Z0-9_]*"),
                    "kod ASCII büyük harf olmalı: " + category.getCode());
        });
    }

    @Test
    void categoryNamesAreLocalisedButCodesAreNot() {
        Map<String, JobCategory> byCode = jobCategoryDao.findAll().stream()
                .collect(Collectors.toMap(JobCategory::getCode, Function.identity()));

        assertEquals("Fotoğrafçılık", byCode.get("PHOTOGRAPHY").getName());
        assertEquals("Hayvan Bakımı", byCode.get("PET_CARE").getName());
    }
}
