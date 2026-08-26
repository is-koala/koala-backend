package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.images.response.UploadImageResponseDto;
import com.exskylab.koala.entities.Image;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MediaUrlResolver.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ImageMapper {

    @Mapping(target = "fileUrl", source = "image", qualifiedByName = "imageToPublicUrl")
    UploadImageResponseDto toUploadImageResponseDto(Image image);
}
