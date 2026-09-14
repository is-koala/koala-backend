package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.job.response.ShortJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.core.helpers.JobAddressVisibility;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, JobCategoryMapper.class, AddressMapper.class, CompanyMapper.class,
                GalleryMapper.class, PricingDtoMapper.class, JobAddressVisibility.class, MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobMapper {

    @Mapping(target = "pricing", source = "job")
    IndividualJobDto toUsersMeJobsPostResponseDto(Job job);

    @Mapping(target = "pricing", source = "job")
    CompanyJobDto toCompaniesCompanyIdJobsPostResponseDto(Job job);

    ShortJobDto toShortJobDto(Job job);

    @Mapping(target = "cityName", source = "address.city.name")
    @Mapping(target = "districtName", source = "address.district.name")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyLogoUrl", source = "company.logo", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "latitude", source = "address.latitude", qualifiedByName = "approximateCoordinate")
    @Mapping(target = "longitude", source = "address.longitude", qualifiedByName = "approximateCoordinate")
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "applicationCount", ignore = true)
    @Mapping(target = "hiredCount", ignore = true)
    @Mapping(target = "addressPrecision", constant = "APPROXIMATE")
    @Mapping(target = "distanceKm", ignore = true)
    @Mapping(target = "pricing", source = "job")
    JobSummaryDto toJobSummaryDto(Job job);

    @Mapping(target = "hiredCount", ignore = true)
    @Mapping(target = "applicationCount", ignore = true)
    @Mapping(target = "acceptingApplications", ignore = true)
    @Mapping(target = "addressPrecision", ignore = true)
    @Mapping(target = "pricing", source = "job")
    JobDetailDto toJobDetailDto(Job job);

    List<JobSummaryDto> toJobSummaryDtoList(List<Job> jobs);
}
