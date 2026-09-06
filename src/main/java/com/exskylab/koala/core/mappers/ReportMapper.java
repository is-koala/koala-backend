package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.dtos.report.response.ReportContentDto;
import com.exskylab.koala.core.dtos.report.response.ReportDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.entities.Report;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class, MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ReportMapper {

    @Mapping(target = "reportedUserId", source = "reportedUser.id")
    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "messageId", source = "message.id")
    @Mapping(target = "imageId", source = "image.id")
    ReportDto toReportDto(Report report);

    List<ReportDto> toReportDtoList(List<Report> reports);

    @Mapping(target = "reportedUserId", source = "report.reportedUser.id")
    @Mapping(target = "jobId", source = "report.job.id")
    @Mapping(target = "messageId", source = "report.message.id")
    @Mapping(target = "imageId", source = "report.image.id")
    AdminReportDto toAdminReportDto(Report report, long reportedUserReportCount);

    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "messageBody", source = "message.body")
    @Mapping(target = "imageUrl", source = "image", qualifiedByName = "imageToPublicUrl")
    ReportContentDto toReportContentDto(Report report);
}
