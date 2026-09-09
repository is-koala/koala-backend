package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.company.response.JobCompanyDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    CompanyReviewDto toCompanyReviewDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    OnlyCompanyDto toOnlyCompanyDto(Company company);

    @Mapping(target = "logoUrl", source = "logo", qualifiedByName = "imageToPublicUrl")
    JobCompanyDto toJobCompanyDto(Company company);
}
