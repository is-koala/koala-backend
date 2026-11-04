package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import java.util.Locale;
import java.util.Set;

@Component
public class UploadValidator {

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

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

        if (folderType == FolderType.IMAGE || folderType == FolderType.IDENTITY_DOCUMENT) {
            rejectOversizedCanvas(file);
        }

        if (folderType == FolderType.DISPUTE_EVIDENCE) {
            rejectContentThatDoesNotMatchTheExtension(file, extension);
        }
    }

    private void rejectContentThatDoesNotMatchTheExtension(MultipartFile file, String extension) {
        byte[] head;
        try (InputStream stream = file.getInputStream()) {
            head = stream.readNBytes(PDF_MAGIC.length);
        } catch (IOException e) {
            throw new ValidationException(StorageMessages.UNSUPPORTED_TYPE);
        }

        if ("pdf".equals(extension)) {
            if (!Arrays.equals(head, PDF_MAGIC)) {
                throw new ValidationException(StorageMessages.UNSUPPORTED_TYPE);
            }
            return;
        }

        rejectOversizedCanvas(file);
    }

    private void rejectOversizedCanvas(MultipartFile file) {
        long maximumPixels = (long) uploadProperties.getMaxImageMegapixels() * 1_000_000L;

        try (ImageInputStream stream = ImageIO.createImageInputStream(file.getInputStream())) {
            if (stream == null) {
                throw new ValidationException(StorageMessages.UNSUPPORTED_TYPE);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ValidationException(StorageMessages.UNSUPPORTED_TYPE);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);

                if (pixels > maximumPixels) {
                    throw new ValidationException(StorageMessages.IMAGE_TOO_LARGE);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
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
            case IMAGE, IDENTITY_DOCUMENT -> uploadProperties.getImageExtensions();
            case PUBLIC_FILE -> uploadProperties.getPublicFileExtensions();
            case FILE -> uploadProperties.getFileExtensions();
            case DISPUTE_EVIDENCE -> uploadProperties.getEvidenceExtensions();
        };
    }

    private long maximumBytes(FolderType folderType) {
        int megabytes = switch (folderType) {
            case IMAGE, IDENTITY_DOCUMENT -> uploadProperties.getMaxImageSizeMb();
            case DISPUTE_EVIDENCE -> uploadProperties.getMaxEvidenceSizeMb();
            case FILE, PUBLIC_FILE -> uploadProperties.getMaxFileSizeMb();
        };

        return (long) megabytes * 1024 * 1024;
    }
}
