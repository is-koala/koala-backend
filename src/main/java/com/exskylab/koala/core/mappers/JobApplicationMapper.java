package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDetailDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicantDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.entities.JobApplication;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, JobMapper.class, JobAssignmentMapper.class, MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobApplicationMapper {

    @Mapping(target = "applicant", source = "user")
    JobApplicationDto toJobsJobIdApplicationsPostResponseDto(JobApplication jobApplication);

    @Mapping(target = "applicant", source = "user")
    @Mapping(target = "cvUrl", source = "user.cv", qualifiedByName = "mediaToPublicUrl")
    JobApplicantDto toJobsJobIdApplicationsGetResponseDto(JobApplication jobApplication);

    List<JobApplicantDto> toJobsJobIdApplicationsGetResponseDtoList(
            List<JobApplication> jobApplications);

    @Mapping(target = "applicant", source = "user")
    JobApplicationDetailDto toJobApplicationsApplicationIdPatchResponseDto(
            JobApplication jobApplication);

    @Mapping(target = "assignmentId", source = "assignment.id")
    MyApplicationDto toMyApplicationDto(JobApplication jobApplication);

    List<MyApplicationDto> toMyApplicationDtoList(List<JobApplication> jobApplications);
}
