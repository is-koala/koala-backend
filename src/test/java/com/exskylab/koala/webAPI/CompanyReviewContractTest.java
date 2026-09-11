package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyReviewContractTest {

    @Test
    void theReviewDtoCarriesAddressAndGalleryIdsWithoutStorageKeys() {
        Set<String> names = Arrays.stream(CompanyReviewDto.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(names.contains("address"), "Onay kuyruğu kayıtlı adresi taşımalı.");
        assertTrue(names.contains("galleryImageIds"),
                "Galeri görselleri depo anahtarı değil görsel kimliği olarak dönmeli.");
        assertFalse(names.contains("fileUrl"));
        assertFalse(names.contains("storageKey"));
        assertFalse(names.contains("file_url"));
    }

    @Test
    void staffGalleryDownloadFollowsTheSignedFilePattern() throws Exception {
        Method method = AdminController.class.getDeclaredMethod(
                "downloadCompanyImage", UUID.class, UUID.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/companies/{companyId}/images/{imageId}/download"},
                mapping.value());
        assertEquals(StaffPermission.COMPANY_APPROVE, permission.value()[0]);
        assertEquals("company.image.download", permission.action());
    }
}
