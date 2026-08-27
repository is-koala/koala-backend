package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.configs.r2.FolderType;
import com.exskylab.koala.core.constants.ImageMessages;
import com.exskylab.koala.core.dtos.images.response.UploadImageResponseDto;
import com.exskylab.koala.core.exceptions.ImageUploadError;
import com.exskylab.koala.core.mappers.ImageMapper;
import com.exskylab.koala.core.utilities.storage.R2StorageService;
import com.exskylab.koala.dataAccess.ImageDao;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class ImageManager implements ImageService {

    private final ImageDao imageDao;
    private final R2StorageService r2StorageService;
    private final SecurityService securityService;
    private final static Logger logger = LoggerFactory.getLogger(ImageManager.class);
    private final ImageMapper imageMapper;


    public ImageManager(ImageDao imageDao, R2StorageService r2StorageService, SecurityService securityService, ImageMapper imageMapper) {
        this.imageDao = imageDao;
        this.r2StorageService = r2StorageService;
        this.securityService = securityService;
        this.imageMapper = imageMapper;
    }


    @Override
    public Image uploadImage(MultipartFile image) {
        logger.info("Uploading image file: {}", image.getOriginalFilename());
        if (image.isEmpty()){
            logger.error("Image upload failed: Image file is empty.");
            throw new ImageUploadError(ImageMessages.IMAGE_EMPTY_ERROR);
        }

        if (image.getSize() > 10 * 1024 * 1024) {
            logger.error("Image upload failed: Image file size exceeds the limit of 10 MB.");
            throw new ImageUploadError(ImageMessages.IMAGE_SIZE_ERROR);
        }

        try {
            byte[] cleanImageBytes = removeMetadata(image);

            String imageKey = r2StorageService.uploadFile(
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
            logger.info("Image uploaded successfully: {}", savedImage.getFileName());
            return savedImage;
        } catch (Exception e) {
            logger.error("Image upload failed fileName: {}, errorMessage: {}", image.getOriginalFilename(), e.getMessage());
            throw new ImageUploadError(ImageMessages.IMAGE_UPLOAD_ERROR);
        }
    }

    @Override
    public UploadImageResponseDto uploadImageDto(MultipartFile image) {
        return imageMapper.toUploadImageResponseDto(uploadImage(image));
    }

    @Override
    public void deleteImage(UUID imageId) {
        logger.info("Deleting image with ID: {}", imageId);
        var image = imageDao.findById(imageId);

        if(image.isEmpty()){
            logger.error("Image deletion failed: Image with ID {} not found.", imageId);
            throw new ImageUploadError(ImageMessages.IMAGE_NOT_FOUND_ERROR);
        }

        User user = securityService.getAuthenticatedUser();
        if (!image.get().getCreatedBy().getId().equals(user.getId()) && !user.getAuthorities().contains(Role.ROLE_ADMIN)) {
            logger.error("Image deletion failed: User {} does not have permission to delete image with ID {}. Image creator ID: {}", user.getId(), imageId, image.get().getCreatedBy().getId());
            throw new ImageUploadError(ImageMessages.IMAGE_PERMISSION_ERROR);
        }

        imageDao.deleteById(imageId);
        logger.info("Image with ID {} soft deleted by userID {}", imageId, user.getId());
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
