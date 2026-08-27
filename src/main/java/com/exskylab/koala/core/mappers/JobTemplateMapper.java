package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobTemplate.response.JobTemplateDto;
import com.exskylab.koala.entities.JobTemplate;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = JobCategoryMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobTemplateMapper {

    @Mapping(target = "companyId", source = "company.id")
    JobTemplateDto toJobTemplateDto(JobTemplate template);

    List<JobTemplateDto> toJobTemplateDtoList(List<JobTemplate> templates);
}
