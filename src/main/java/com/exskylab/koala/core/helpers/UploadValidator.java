package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Component
public class UploadValidator {

    private final UploadProperties uploadProperties;

    public UploadValidator(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public void validate(MultipartFile file, FolderType folderType, String emptyMessage, String sizeMessage) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(emptyMessage);
        }

        long maximumBytes = maximumBytes(folderType);
        if (file.getSize() > maximumBytes) {
            throw new ValidationException(sizeMessage);
        }

        Set<String> allowed = allowedExtensions(folderType);
        String extension = extensionOf(file.getOriginalFilename());

        if (extension == null || !allowed.contains(extension)) {
            throw new ValidationException(StorageMessages.UNSUPPORTED_TYPE);
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }

        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedExtensions(FolderType folderType) {
        return switch (folderType) {
            case IMAGE -> uploadProperties.getImageExtensions();
            case PUBLIC_FILE -> uploadProperties.getPublicFileExtensions();
            case FILE -> uploadProperties.getFileExtensions();
        };
    }

    private long maximumBytes(FolderType folderType) {
        int megabytes = folderType == FolderType.IMAGE
                ? uploadProperties.getMaxImageSizeMb()
                : uploadProperties.getMaxFileSizeMb();

        return (long) megabytes * 1024 * 1024;
    }
}
