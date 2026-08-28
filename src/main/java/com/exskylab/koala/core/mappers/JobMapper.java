package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.job.response.ShortJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.entities.Job;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, JobCategoryMapper.class, AddressMapper.class, CompanyMapper.class,
                GalleryMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobMapper {

    IndividualJobDto toUsersMeJobsPostResponseDto(Job job);

    CompanyJobDto toCompaniesCompanyIdJobsPostResponseDto(Job job);

    ShortJobDto toShortJobDto(Job job);

    @Mapping(target = "cityName", source = "address.city.name")
    @Mapping(target = "districtName", source = "address.district.name")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "latitude", source = "address.latitude")
    @Mapping(target = "longitude", source = "address.longitude")
    @Mapping(target = "coverImageUrl", ignore = true)
    JobSummaryDto toJobSummaryDto(Job job);

    JobDetailDto toJobDetailDto(Job job);

    List<JobSummaryDto> toJobSummaryDtoList(List<Job> jobs);
}
