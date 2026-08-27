package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.report.response.ReportDto;
import com.exskylab.koala.entities.Report;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ReportMapper {

    @Mapping(target = "reportedUserId", source = "reportedUser.id")
    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "messageId", source = "message.id")
    ReportDto toReportDto(Report report);

    List<ReportDto> toReportDtoList(List<Report> reports);
}
