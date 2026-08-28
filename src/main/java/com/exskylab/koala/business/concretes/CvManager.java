package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.constants.CvMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.MediaDao;
import com.exskylab.koala.entities.File;
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

    public CvManager(MediaDao mediaDao, UserService userService, StorageService storageService,
                     SecurityService securityService, MediaUrlResolver mediaUrlResolver,
                     UploadValidator uploadValidator) {
        this.mediaDao = mediaDao;
        this.userService = userService;
        this.storageService = storageService;
        this.securityService = securityService;
        this.mediaUrlResolver = mediaUrlResolver;
        this.uploadValidator = uploadValidator;
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
        } catch (IOException e) {
            log.error("CV upload failed for user {}: {}", currentUser.getId(), e.getMessage());
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
