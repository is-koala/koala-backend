package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import com.exskylab.koala.entities.WorkSession;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TimesheetMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    WorkSessionDto toWorkSessionDto(WorkSession session);

    List<WorkSessionDto> toWorkSessionDtoList(List<WorkSession> sessions);
}
