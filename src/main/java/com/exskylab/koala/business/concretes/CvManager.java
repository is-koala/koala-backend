package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.constants.CvMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageRollbackCleanup;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.MediaDao;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.entities.File;
import com.exskylab.koala.entities.Media;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CvManager implements CvService {

    private final MediaDao mediaDao;
    private final UserService userService;
    private final StorageService storageService;
    private final SecurityService securityService;
    private final UploadValidator uploadValidator;
    private final MediaUrlResolver mediaUrlResolver;
    private final JobApplicationService jobApplicationService;

    public CvManager(MediaDao mediaDao, UserService userService, StorageService storageService,
                     SecurityService securityService, MediaUrlResolver mediaUrlResolver,
                     UploadValidator uploadValidator, JobApplicationService jobApplicationService) {
        this.mediaDao = mediaDao;
        this.userService = userService;
        this.storageService = storageService;
        this.securityService = securityService;
        this.mediaUrlResolver = mediaUrlResolver;
        this.uploadValidator = uploadValidator;
        this.jobApplicationService = jobApplicationService;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanDownload(String storageKey) {
        Media media = mediaDao.findByFileUrl(storageKey)
                .orElseThrow(() -> new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND));

        User user = securityService.getAuthenticatedUser();
        UUID ownerId = media.getCreatedBy() == null ? null : media.getCreatedBy().getId();

        if (user.getId().equals(ownerId) || user.getAuthorities().contains(Role.ROLE_ADMIN)) {
            return;
        }

        if (ownerId != null && jobApplicationService.hasApplicationForEmployer(user.getId(), ownerId)) {
            log.info("Employer {} downloaded the cv of applicant {}", user.getId(), ownerId);
            return;
        }

        log.warn("User {} tried to download private object {} owned by {}", user.getId(), storageKey, ownerId);
        throw new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND);
    }

    @Override
    @Transactional
    public File uploadCv(MultipartFile document) {
        uploadValidator.validate(document, FolderType.FILE, CvMessages.EMPTY, CvMessages.SIZE_EXCEEDED);

        User currentUser = securityService.getAuthenticatedUser();

        String key;
        try {
            key = storageService.uploadFile(document.getBytes(), document.getOriginalFilename(),
                    document.getContentType(), FolderType.FILE);
            StorageRollbackCleanup.deleteOnRollback(storageService, key);
        } catch (IOException e) {
            log.error("CV upload failed for user {}", currentUser.getId(), e);
            throw new ValidationException(CvMessages.UPLOAD_FAILED);
        }

        File cv = new File();
        cv.setFileName(document.getOriginalFilename());
        cv.setFileType(document.getContentType());
        cv.setFileUrl(key);
        cv.setFileSize(document.getSize());

        File saved = mediaDao.save(cv);
        currentUser.setCv(saved);
        userService.save(currentUser);

        log.info("CV uploaded for user {}", currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteCv() {
        User currentUser = securityService.getAuthenticatedUser();
        if (currentUser.getCv() == null) {
            throw new ResourceNotFoundException(CvMessages.NOT_FOUND);
        }

        File cv = currentUser.getCv();
        currentUser.setCv(null);
        userService.save(currentUser);
        mediaDao.delete(cv);

        log.info("CV removed for user {}", currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveCvUrl(UUID userId) {
        return java.util.Optional.of(userService.getById(userId))
                .map(User::getCv)
                .map(mediaUrlResolver::toFileUrl)
                .orElse(null);
    }
}
