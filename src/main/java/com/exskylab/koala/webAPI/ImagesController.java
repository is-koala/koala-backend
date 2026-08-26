package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.core.constants.ImageMessages;
import com.exskylab.koala.core.dtos.images.response.UploadImageResponseDto;
import com.exskylab.koala.core.mappers.ImageMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.Image;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/images")
public class ImagesController {

    private final ImageService imageService;


    public ImagesController(ImageService imageService) {
        this.imageService = imageService;
    }



    @PostMapping("/")
    public ResponseEntity<SuccessDataResult<UploadImageResponseDto>> uploadImage(@RequestParam("image") MultipartFile image){

        UploadImageResponseDto responseImage = imageService.uploadImageDto(image);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<UploadImageResponseDto>(responseImage,
                        ImageMessages.UPLOAD_IMAGE_SUCCESS,
                        HttpStatus.CREATED
        ));

    }


    @DeleteMapping("/{imageId}")
    public ResponseEntity<SuccessResult> deleteImage(@PathVariable UUID imageId) {

        imageService.deleteImage(imageId);

        return ResponseEntity.ok(
                new SuccessResult(ImageMessages.DELETE_IMAGE_SUCCESS,
                        HttpStatus.OK
        ));
    }

}
