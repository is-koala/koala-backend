package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationsApplicationIdPatchResponseDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobsJobIdApplicationsGetResponseDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobsJobIdApplicationsPostResponseDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.entities.JobApplication;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, JobMapper.class, JobAssignmentMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobApplicationMapper {

    @Mapping(target = "applicant", source = "user")
    JobsJobIdApplicationsPostResponseDto toJobsJobIdApplicationsPostResponseDto(JobApplication jobApplication);

    @Mapping(target = "applicant", source = "user")
    JobsJobIdApplicationsGetResponseDto toJobsJobIdApplicationsGetResponseDto(JobApplication jobApplication);

    List<JobsJobIdApplicationsGetResponseDto> toJobsJobIdApplicationsGetResponseDtoList(
            List<JobApplication> jobApplications);

    @Mapping(target = "applicant", source = "user")
    JobApplicationsApplicationIdPatchResponseDto toJobApplicationsApplicationIdPatchResponseDto(
            JobApplication jobApplication);

    @Mapping(target = "assignmentId", source = "assignment.id")
    MyApplicationDto toMyApplicationDto(JobApplication jobApplication);

    List<MyApplicationDto> toMyApplicationDtoList(List<JobApplication> jobApplications);
}
