package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.dtos.jobCategory.request.SaveJobCategoryDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobAlertDao;
import com.exskylab.koala.dataAccess.JobCategoryDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.JobTemplateDao;
import com.exskylab.koala.entities.JobCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobCategoryAdministrationRulesTest {

    private JobCategoryDao jobCategoryDao;
    private JobDao jobDao;
    private JobAlertDao jobAlertDao;
    private JobTemplateDao jobTemplateDao;
    private JobCategoryManager manager;

    @BeforeEach
    void setUp() {
        jobCategoryDao = mock(JobCategoryDao.class);
        jobDao = mock(JobDao.class);
        jobAlertDao = mock(JobAlertDao.class);
        jobTemplateDao = mock(JobTemplateDao.class);
        manager = new JobCategoryManager(jobCategoryDao, jobDao, jobAlertDao, jobTemplateDao);

        when(jobCategoryDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SaveJobCategoryDto request(String code, String name, boolean requiresInsurance) {
        SaveJobCategoryDto request = new SaveJobCategoryDto();
        request.setCode(code);
        request.setName(name);
        request.setRequiresInsurance(requiresInsurance);
        return request;
    }

    private JobCategory existing(String code, String name) {
        JobCategory category = new JobCategory();
        category.setId(UUID.randomUUID());
        category.setCode(code);
        category.setName(name);
        when(jobCategoryDao.findById(category.getId())).thenReturn(Optional.of(category));
        return category;
    }

    @Test
    void anAdminCreatesACategoryWithItsCodeNameAndInsuranceFlag() {
        when(jobCategoryDao.existsByCode("BARISTA")).thenReturn(false);

        JobCategory created = manager.createCategory(request("BARISTA", "Barista", true));

        assertEquals("BARISTA", created.getCode());
        assertEquals("Barista", created.getName());
        assertTrue(created.isRequiresInsurance());
        verify(jobCategoryDao).save(any(JobCategory.class));
    }

    @Test
    void aDuplicateCodeIsRejectedBeforeItReachesTheUniqueConstraint() {
        when(jobCategoryDao.existsByCode("BARISTA")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> manager.createCategory(request("BARISTA", "Barista", false)));

        verify(jobCategoryDao, never()).save(any());
    }

    @Test
    void anUpdateRewritesTheCodeNameAndInsuranceFlag() {
        JobCategory existing = existing("BARISTA", "Barista");

        JobCategory updated = manager.updateCategory(existing.getId(),
                request("COFFEE", "Kahve Ustası", true));

        assertEquals("COFFEE", updated.getCode());
        assertEquals("Kahve Ustası", updated.getName());
        assertTrue(updated.isRequiresInsurance());
        assertEquals(existing.getId(), updated.getId());
    }

    @Test
    void takingACodeAnotherCategoryOwnsIsRejected() {
        JobCategory existing = existing("BARISTA", "Barista");
        when(jobCategoryDao.existsByCodeAndIdNot("WAITER", existing.getId())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> manager.updateCategory(existing.getId(), request("WAITER", "Garsonluk", false)));

        verify(jobCategoryDao, never()).save(any());
    }

    @Test
    void keepingItsOwnCodeIsNotAConflict() {
        JobCategory existing = existing("BARISTA", "Barista");
        when(jobCategoryDao.existsByCodeAndIdNot("BARISTA", existing.getId())).thenReturn(false);

        JobCategory updated = manager.updateCategory(existing.getId(),
                request("BARISTA", "Barista Ustası", false));

        assertEquals("Barista Ustası", updated.getName());
    }

    @Test
    void updatingACategoryThatDoesNotExistIsANotFound() {
        UUID unknown = UUID.randomUUID();
        when(jobCategoryDao.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> manager.updateCategory(unknown, request("BARISTA", "Barista", false)));
    }

    @Test
    void aCategoryNothingReferencesIsDeleted() {
        JobCategory existing = existing("BARISTA", "Barista");

        manager.deleteCategory(existing.getId());

        verify(jobCategoryDao).delete(existing);
    }

    @Test
    void aCategoryAJobReferencesCannotBeDeleted() {
        JobCategory existing = existing("BARISTA", "Barista");
        when(jobDao.existsAnyByCategoryId(existing.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> manager.deleteCategory(existing.getId()));

        verify(jobCategoryDao, never()).delete(any());
    }

    @Test
    void aCategoryAJobAlertReferencesCannotBeDeleted() {
        JobCategory existing = existing("BARISTA", "Barista");
        when(jobAlertDao.existsAnyByCategoryId(existing.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> manager.deleteCategory(existing.getId()));

        verify(jobCategoryDao, never()).delete(any());
    }

    @Test
    void aCategoryAJobTemplateReferencesCannotBeDeleted() {
        JobCategory existing = existing("BARISTA", "Barista");
        when(jobTemplateDao.existsAnyByCategoryId(existing.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> manager.deleteCategory(existing.getId()));

        verify(jobCategoryDao, never()).delete(any());
    }

    @Test
    void deletingACategoryThatDoesNotExistIsANotFound() {
        UUID unknown = UUID.randomUUID();
        when(jobCategoryDao.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> manager.deleteCategory(unknown));
    }
}
