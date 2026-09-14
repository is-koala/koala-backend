package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.company.response.CompanySummaryDto;
import com.exskylab.koala.core.dtos.company.response.JobCompanyDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyImage;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Mapper(componentModel = "spring",
        uses = {MediaUrlResolver.class, CompanyContactMapper.class, AddressMapper.class, GalleryMapper.class,
                UserMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompanyMapper {

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "companyContacts", source = "contacts")
    @Mapping(target = "followerCount", ignore = true)
    @Mapping(target = "following", ignore = true)
    @Mapping(target = "approvalNote", ignore = true)
    @Mapping(target = "approved", expression = "java(company.getApprovalStatus().isApproved())")
    CompanyDto toCompanyDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "galleryImageIds", source = "images", qualifiedByName = "companyGalleryImageIds")
    CompanyReviewDto toCompanyReviewDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    OnlyCompanyDto toOnlyCompanyDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    JobCompanyDto toJobCompanyDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    CompanySummaryDto toCompanySummaryDto(Company company);

    @Named("companyGalleryImageIds")
    default List<UUID> companyGalleryImageIds(List<CompanyImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .map(CompanyImage::getImage)
                .filter(Objects::nonNull)
                .map(Image::getId)
                .toList();
    }
}
