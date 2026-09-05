package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.core.dtos.jobCategory.request.SaveJobCategoryDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.dataAccess.JobCategoryDao;
import com.exskylab.koala.entities.JobCategory;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobCategoryAdministrationIT extends AbstractIntegrationTest {

    @Autowired
    private JobCategoryService jobCategoryService;

    @Autowired
    private JobCategoryDao jobCategoryDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from jobs where job_category_id in "
                + "(select id from job_categories where code like 'GUARD_%')");
        jdbcTemplate.update("delete from job_categories where code like 'GUARD_%'");
    }

    private JobCategory category(String code) {
        SaveJobCategoryDto request = new SaveJobCategoryDto();
        request.setCode(code);
        request.setName("Deneme " + code);
        request.setRequiresInsurance(false);
        return jobCategoryService.createCategory(request);
    }

    private void referenceFromASoftDeletedJob(UUID categoryId) {
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, job_category_id)
                values (?, now(), true, ?)
                """, UUID.randomUUID(), categoryId);
    }

    @Test
    void aCategoryASoftDeletedJobStillReferencesCannotBeDeleted() {
        JobCategory category = category("GUARD_SOFT_DELETED");
        referenceFromASoftDeletedJob(category.getId());

        assertThrows(ConflictException.class, () -> jobCategoryService.deleteCategory(category.getId()));

        assertTrue(jobCategoryDao.findById(category.getId()).isPresent(),
                "Yabanci anahtar hala isaret ediyorken kategori silinmemeliydi.");
    }

    @Test
    void aCategoryNothingReferencesIsRemovedFromTheTable() {
        JobCategory category = category("GUARD_FREE");

        jobCategoryService.deleteCategory(category.getId());

        assertTrue(jobCategoryDao.findById(category.getId()).isEmpty());
    }

    @Test
    void theUniqueCodeConstraintIsAnsweredWithADomainConflict() {
        category("GUARD_DUPLICATE");

        assertThrows(ConflictException.class, () -> category("GUARD_DUPLICATE"));
    }
}
