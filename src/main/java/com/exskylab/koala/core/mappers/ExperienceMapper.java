package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.experience.response.ExperienceDto;
import com.exskylab.koala.entities.Experience;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ExperienceMapper {

    ExperienceDto toExperienceDto(Experience experience);

    List<ExperienceDto> toExperienceDtoList(List<Experience> experiences);
}
