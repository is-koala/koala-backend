package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobCategory;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JobCategoryMapper {

    JobCategoryDto toCategoryDto(JobCategory jobCategory);

    List<JobCategoryDto> toCategoryDtoList(List<JobCategory> jobCategories);
}
