package com.exskylab.koala.core.utilities.storage;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.constants.StorageMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
    }

    @Override
    public String uploadFile(byte[] fileBytes, String originalFileName, String contentType, FolderType folderType) {
        String key = StorageKeys.newKey(folderType, originalFileName);

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentDisposition(FileDispositions.inline(originalFileName))
                    .build(), RequestBody.fromBytes(fileBytes));
        } catch (RuntimeException e) {
            log.error("S3 upload failed for {}", key, e);
            throw new ValidationException(StorageMessages.UPLOAD_FAILED);
        }

        log.info("Uploaded to S3: {}", key);
        return key;
    }

    @Override
    public void deleteFile(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(key)
                    .build());
        } catch (RuntimeException e) {
            log.error("S3 delete failed for {}", key, e);
            throw new ValidationException(StorageMessages.DELETE_FAILED);
        }

        log.info("Deleted from S3: {}", key);
    }

    @Override
    public byte[] readFile(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException(StorageMessages.FILE_NOT_FOUND);
        } catch (RuntimeException e) {
            log.error("S3 read failed for {}", key, e);
            throw new ValidationException(StorageMessages.DOWNLOAD_FAILED);
        }
    }

    @Override
    public boolean supportsSignedUrls() {
        return true;
    }

    @Override
    public String temporaryUrl(String key) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(storageProperties.getSignedUrlMinutes()))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(storageProperties.getBucket())
                        .key(key)
                        .build())
                .build();

        return s3Presigner.presignGetObject(request).url().toString();
    }
}
