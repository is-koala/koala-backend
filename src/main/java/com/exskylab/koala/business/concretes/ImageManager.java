package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.constants.ImageMessages;
import com.exskylab.koala.core.dtos.images.response.UploadImageResponseDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.mappers.ImageMapper;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.ImageDao;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.UploadValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class ImageManager implements ImageService {

    private final ImageDao imageDao;
    private final StorageService storageService;
    private final SecurityService securityService;
    private final ImageMapper imageMapper;
    private final UploadValidator uploadValidator;


    public ImageManager(ImageDao imageDao, StorageService storageService, SecurityService securityService,
                        ImageMapper imageMapper, UploadValidator uploadValidator) {
        this.imageDao = imageDao;
        this.storageService = storageService;
        this.securityService = securityService;
        this.imageMapper = imageMapper;
        this.uploadValidator = uploadValidator;
    }


    @Override
    public Image getById(UUID imageId) {
        return imageDao.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(ImageMessages.IMAGE_NOT_FOUND_ERROR));
    }

    @Override
    public Image uploadImage(MultipartFile image) {
        log.info("Uploading image file: {}", image.getOriginalFilename());
        uploadValidator.validate(image, FolderType.IMAGE,
                ImageMessages.IMAGE_EMPTY_ERROR, ImageMessages.IMAGE_SIZE_ERROR);

        try {
            byte[] cleanImageBytes = removeMetadata(image);

            String imageKey = storageService.uploadFile(
                    cleanImageBytes,
                    image.getOriginalFilename(),
                    image.getContentType(),
                    FolderType.IMAGE);


            Image newImage = new Image();
            newImage.setFileName(image.getOriginalFilename());
            newImage.setFileType(image.getContentType());
            newImage.setFileUrl(imageKey);
            newImage.setFileSize((long) cleanImageBytes.length);

            var savedImage = imageDao.save(newImage);
            log.info("Image uploaded successfully: {}", savedImage.getFileName());
            return savedImage;
        } catch (Exception e) {
            log.error("Image upload failed fileName: {}, errorMessage: {}", image.getOriginalFilename(), e.getMessage());
            throw new ValidationException(ImageMessages.IMAGE_UPLOAD_ERROR);
        }
    }

    @Override
    public UploadImageResponseDto uploadImageDto(MultipartFile image) {
        return imageMapper.toUploadImageResponseDto(uploadImage(image));
    }

    @Override
    public void deleteImage(UUID imageId) {
        log.info("Deleting image with ID: {}", imageId);
        var image = imageDao.findById(imageId);

        if(image.isEmpty()){
            log.error("Image deletion failed: Image with ID {} not found.", imageId);
            throw new ValidationException(ImageMessages.IMAGE_NOT_FOUND_ERROR);
        }

        User user = securityService.getAuthenticatedUser();
        if (!image.get().getCreatedBy().getId().equals(user.getId()) && !user.getAuthorities().contains(Role.ROLE_ADMIN)) {
            log.error("Image deletion failed: User {} does not have permission to delete image with ID {}. Image creator ID: {}", user.getId(), imageId, image.get().getCreatedBy().getId());
            throw new ValidationException(ImageMessages.IMAGE_PERMISSION_ERROR);
        }

        imageDao.deleteById(imageId);
        log.info("Image with ID {} soft deleted by userID {}", imageId, user.getId());
    }


    private byte[] removeMetadata(MultipartFile image) throws IOException {
        BufferedImage originalImage = ImageIO.read(image.getInputStream());

        if (originalImage == null){
            throw new IOException("Image file is empty.");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String formatName = getFileExtension(image.getOriginalFilename()).substring(1);


        ImageIO.write(originalImage, formatName, outputStream);

        return outputStream.toByteArray();
    }

    private String getFileExtension(String fileName) {

        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("File has no extension: " + fileName);
        }

        return fileName.substring(fileName.lastIndexOf("."));

    }
}
