package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.entities.CompanyImage;
import com.exskylab.koala.entities.JobImage;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface GalleryMapper {

    @Mapping(target = "id", source = "image.id")
    @Mapping(target = "url", source = "image", qualifiedByName = "imageToPublicUrl")
    GalleryImageDto toGalleryImageDto(CompanyImage companyImage);

    @Mapping(target = "id", source = "image.id")
    @Mapping(target = "url", source = "image", qualifiedByName = "imageToPublicUrl")
    GalleryImageDto toGalleryImageDto(JobImage jobImage);

    List<GalleryImageDto> toCompanyGalleryDtoList(List<CompanyImage> companyImages);

    List<GalleryImageDto> toJobGalleryDtoList(List<JobImage> jobImages);
}
