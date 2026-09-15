package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.dtos.report.response.ReportContentDto;
import com.exskylab.koala.core.dtos.report.response.ReportDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {UserMapper.class, MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ReportMapper {

    @Mapping(target = "reportedUserId", source = "reportedUser.id")
    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "messageId", source = "message.id")
    @Mapping(target = "imageId", source = "image.id")
    ReportDto toReportDto(Report report);

    List<ReportDto> toReportDtoList(List<Report> reports);

    @Mapping(target = "id", source = "report.id")
    @Mapping(target = "reporter", source = "reporter")
    @Mapping(target = "reportedUser", source = "report.reportedUser")
    @Mapping(target = "reportedUserId", source = "report.reportedUser.id")
    @Mapping(target = "jobId", source = "report.job.id")
    @Mapping(target = "jobAssignmentId", source = "jobAssignmentId")
    @Mapping(target = "messageId", source = "report.message.id")
    @Mapping(target = "imageId", source = "report.image.id")
    @Mapping(target = "reason", source = "report.reason")
    @Mapping(target = "description", source = "report.description")
    @Mapping(target = "status", source = "report.status")
    @Mapping(target = "reviewedBy", source = "report.reviewedBy")
    @Mapping(target = "reviewedByStaffId", source = "report.reviewedByStaff.id")
    @Mapping(target = "reviewNote", source = "report.reviewNote")
    @Mapping(target = "reviewedAt", source = "report.reviewedAt")
    @Mapping(target = "createdAt", source = "report.createdAt")
    @Mapping(target = "reportedUserReportCount", source = "reportedUserReportCount")
    AdminReportDto toAdminReportDto(Report report, User reporter, long reportedUserReportCount, UUID jobAssignmentId);

    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "messageBody", source = "message.body")
    @Mapping(target = "imageUrl", source = "image", qualifiedByName = "imageToPublicUrl")
    ReportContentDto toReportContentDto(Report report);
}
