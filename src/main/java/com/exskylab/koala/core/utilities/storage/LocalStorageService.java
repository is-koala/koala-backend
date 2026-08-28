package com.exskylab.koala.core.utilities.storage;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

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
            log.info("File stored locally: {}", target);
            return key;
        } catch (IOException e) {
            log.error("Local file store failed for {}: {}", key, e.getMessage());
            throw new ValidationException(StorageMessages.UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            Files.deleteIfExists(resolve(key));
            log.info("Local file deleted: {}", key);
        } catch (IOException e) {
            log.error("Local file delete failed for {}: {}", key, e.getMessage());
            throw new ValidationException(StorageMessages.DELETE_FAILED);
        }
    }

    @Override
    public byte[] readFile(String key) {
        try {
            Path source = resolve(key);
            if (!Files.exists(source)) {
                throw new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND);
            }
            return Files.readAllBytes(source);
        } catch (IOException e) {
            log.error("Local file read failed for {}: {}", key, e.getMessage());
            throw new ValidationException(StorageMessages.DOWNLOAD_FAILED);
        }
    }

    @Override
    public boolean supportsSignedUrls() {
        return false;
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
