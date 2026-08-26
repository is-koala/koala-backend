package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.job.response.CompaniesCompanyIdJobsPostResponseDto;
import com.exskylab.koala.core.dtos.job.response.ShortJobDto;
import com.exskylab.koala.core.dtos.job.response.UsersMeJobsPostResponseDto;
import com.exskylab.koala.entities.Job;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, JobCategoryMapper.class, AddressMapper.class, CompanyMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobMapper {

    UsersMeJobsPostResponseDto toUsersMeJobsPostResponseDto(Job job);

    CompaniesCompanyIdJobsPostResponseDto toCompaniesCompanyIdJobsPostResponseDto(Job job);

    ShortJobDto toShortJobDto(Job job);
}
