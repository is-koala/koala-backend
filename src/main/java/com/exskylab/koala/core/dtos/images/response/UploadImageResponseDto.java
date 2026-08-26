package com.exskylab.koala.core.dtos.images.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UploadImageResponseDto {

    private UUID id;

    private String fileName;

    private String fileType;

    private String fileUrl;

    private Long fileSize;

}
