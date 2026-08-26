package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobCategoryMapper {

    JobCategoryDto toCategoryDto(JobCategory jobCategory);
}
