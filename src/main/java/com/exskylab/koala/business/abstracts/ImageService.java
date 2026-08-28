package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.images.response.UploadImageResponseDto;
import com.exskylab.koala.entities.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ImageService {
    Image getById(UUID imageId);

    Image uploadImage(MultipartFile image);

    UploadImageResponseDto uploadImageDto(MultipartFile image);

    void deleteImage(UUID imageId);

}
