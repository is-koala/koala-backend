package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.MediaDao;
import com.exskylab.koala.entities.File;
import com.exskylab.koala.entities.Media;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CvAccessRulesTest {

    private static final String CV_KEY = "files/9f1c0f10-0001-4c00-8000-000000000001.pdf";

    private MediaDao mediaDao;
    private SecurityService securityService;
    private JobApplicationService jobApplicationService;
    private CvManager manager;

    private final User worker = user(Role.ROLE_USER);

    @BeforeEach
    void setUp() {
        mediaDao = mock(MediaDao.class);
        securityService = mock(SecurityService.class);
        jobApplicationService = mock(JobApplicationService.class);

        manager = new CvManager(mediaDao, mock(UserService.class), mock(StorageService.class),
                securityService, mock(MediaUrlResolver.class), mock(UploadValidator.class),
                jobApplicationService);

        Media cv = new File();
        cv.setId(UUID.randomUUID());
        cv.setFileUrl(CV_KEY);
        cv.setCreatedBy(worker);

        when(mediaDao.findByFileUrl(CV_KEY)).thenReturn(Optional.of(cv));
    }

    private static User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAuthorities(Set.of(role));
        return user;
    }

    @Test
    void theOwnerCanAlwaysDownloadTheirOwnCv() {
        when(securityService.getAuthenticatedUser()).thenReturn(worker);

        assertDoesNotThrow(() -> manager.assertCanDownload(CV_KEY));
        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void anEmployerTheWorkerAppliedToCanDownloadIt() {
        User employer = user(Role.ROLE_USER);
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        when(jobApplicationService.hasApplicationForEmployer(employer.getId(), worker.getId()))
                .thenReturn(true);

        assertDoesNotThrow(() -> manager.assertCanDownload(CV_KEY));
    }

    @Test
    void anEmployerWithNoApplicationFromThatWorkerCannot() {
        User stranger = user(Role.ROLE_USER);
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);
        when(jobApplicationService.hasApplicationForEmployer(any(), any())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> manager.assertCanDownload(CV_KEY));
    }

    @Test
    void anAdminCanDownloadWithoutAnyApplication() {
        when(securityService.getAuthenticatedUser()).thenReturn(user(Role.ROLE_ADMIN));

        assertDoesNotThrow(() -> manager.assertCanDownload(CV_KEY));
        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void aDeletedCvIsNotDownloadableByAnyone() {
        when(mediaDao.findByFileUrl(CV_KEY)).thenReturn(Optional.empty());
        when(securityService.getAuthenticatedUser()).thenReturn(worker);

        assertThrows(ResourceNotFoundException.class, () -> manager.assertCanDownload(CV_KEY));
    }
}
