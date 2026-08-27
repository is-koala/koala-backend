package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAlert.response.JobAlertDto;
import com.exskylab.koala.entities.JobAlert;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = JobCategoryMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobAlertMapper {

    JobAlertDto toJobAlertDto(JobAlert alert);

    List<JobAlertDto> toJobAlertDtoList(List<JobAlert> alerts);
}
