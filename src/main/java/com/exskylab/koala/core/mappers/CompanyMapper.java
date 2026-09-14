package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.company.response.CompanySummaryDto;
import com.exskylab.koala.core.dtos.company.response.JobCompanyDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
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

    @Mapping(target = "id", source = "company.id")
    @Mapping(target = "name", source = "company.name")
    @Mapping(target = "email", source = "company.email")
    @Mapping(target = "phoneNumber", source = "company.phoneNumber")
    @Mapping(target = "taxNumber", source = "company.taxNumber")
    @Mapping(target = "type", source = "company.type")
    @Mapping(target = "logoUrl", source = "company.logo", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "website", source = "company.website")
    @Mapping(target = "description", source = "company.description")
    @Mapping(target = "approvalStatus", source = "company.approvalStatus")
    @Mapping(target = "approvalNote", source = "company.approvalNote")
    @Mapping(target = "approvalDecidedAt", source = "company.approvalDecidedAt")
    @Mapping(target = "approvedByStaffId", source = "company.approvedByStaffId")
    @Mapping(target = "createdAt", source = "company.createdAt")
    @Mapping(target = "createdBy", source = "company.createdBy")
    @Mapping(target = "address", source = "company.address")
    @Mapping(target = "galleryImageIds", source = "company.images", qualifiedByName = "companyGalleryImageIds")
    @Mapping(target = "companyContacts", source = "contacts")
    @Mapping(target = "jobCount", source = "jobCount")
    CompanyReviewDto toCompanyReviewDto(Company company, List<CompanyContact> contacts, Long jobCount);

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
