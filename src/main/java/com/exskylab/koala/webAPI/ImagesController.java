package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Görseller", description = "Bağımsız görsel yükleme ve silme.")
@RestController
@RequestMapping("/images")
public class ImagesController {

    private final ImageService imageService;


    public ImagesController(ImageService imageService) {
        this.imageService = imageService;
    }



    @Operation(summary = "Görsel yükle",
            description = "Metadata temizlenerek depoya yazılır. Kimliği doğrulanmış her kullanıcıya açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Görsel yüklendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "413", description = "Tek dosya 10 MiB ya da toplam multipart gövdesi 42 MiB sınırını aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping
    public ResponseEntity<SuccessDataResult<UploadImageResponseDto>> uploadImage(@RequestParam("image") MultipartFile image){

        UploadImageResponseDto responseImage = imageService.uploadImageDto(image);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<UploadImageResponseDto>(responseImage,
                        ImageMessages.UPLOAD_IMAGE_SUCCESS,
                        HttpStatus.CREATED
        ));

    }


    @Operation(summary = "Görseli sil",
            description = "Yalnızca görseli yükleyen kullanıcı ya da ADMIN silebilir. Kayıt soft delete edilir, depodaki nesne durur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel silindi"),
            @ApiResponse(responseCode = "403", description = "Bu görseli silme yetkiniz yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{imageId}")
    public ResponseEntity<SuccessResult> deleteImage(@PathVariable UUID imageId) {

        imageService.deleteImage(imageId);

        return ResponseEntity.ok(
                new SuccessResult(ImageMessages.DELETE_IMAGE_SUCCESS,
                        HttpStatus.OK
        ));
    }

}
