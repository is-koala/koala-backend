package com.exskylab.koala.core.utilities.storage;

import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.exceptions.ImageUploadError;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootPath;
    private final AppProperties appProperties;

    public LocalStorageService(StorageProperties storageProperties, AppProperties appProperties) {
        this.rootPath = Path.of(storageProperties.getLocal().getPath()).toAbsolutePath().normalize();
        this.appProperties = appProperties;
    }

    @Override
    public String uploadFile(byte[] fileBytes, String originalFileName, String contentType, FolderType folderType) {
        String key = StorageKeys.newKey(folderType, originalFileName);

        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, fileBytes);
            logger.info("File stored locally: {}", target);
            return key;
        } catch (IOException e) {
            logger.error("Local file store failed for {}: {}", key, e.getMessage());
            throw new ImageUploadError(StorageMessages.UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            Files.deleteIfExists(resolve(key));
            logger.info("Local file deleted: {}", key);
        } catch (IOException e) {
            logger.error("Local file delete failed for {}: {}", key, e.getMessage());
            throw new ImageUploadError(StorageMessages.DELETE_FAILED);
        }
    }

    @Override
    public String temporaryUrl(String key) {
        return appProperties.apiUrl() + "/uploads/" + key;
    }

    private Path resolve(String key) throws IOException {
        Path target = rootPath.resolve(key).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IOException("Invalid storage key: " + key);
        }
        return target;
    }
}
