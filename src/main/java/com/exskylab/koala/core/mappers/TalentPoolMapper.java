package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.talent.response.TalentPoolEntryDto;
import com.exskylab.koala.entities.TalentPoolEntry;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TalentPoolMapper {

    @Mapping(target = "companyId", source = "company.id")
    TalentPoolEntryDto toTalentPoolEntryDto(TalentPoolEntry entry);

    List<TalentPoolEntryDto> toTalentPoolEntryDtoList(List<TalentPoolEntry> entries);
}
