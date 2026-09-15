package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.staff.response.StaffFlagDto;
import com.exskylab.koala.core.dtos.staff.response.StaffNoteDto;
import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffNote;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffNoteMapper {

    @Mapping(target = "authorStaffId", source = "author.id")
    @Mapping(target = "authorDisplayName", source = "author.displayName")
    StaffNoteDto toNoteDto(StaffNote note);

    List<StaffNoteDto> toNoteDtoList(List<StaffNote> notes);

    @Mapping(target = "setByStaffId", source = "setBy.id")
    @Mapping(target = "setByDisplayName", source = "setBy.displayName")
    StaffFlagDto toFlagDto(StaffFlag flag);

    List<StaffFlagDto> toFlagDtoList(List<StaffFlag> flags);
}
