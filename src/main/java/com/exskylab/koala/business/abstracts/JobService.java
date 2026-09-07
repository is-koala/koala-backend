package com.exskylab.koala.business.abstracts;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteriaDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.entities.Job;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface JobService {

    List<GalleryImageDto> getJobGallery(UUID jobId);

    List<GalleryImageDto> addJobImage(UUID jobId, MultipartFile file);

    List<GalleryImageDto> replaceJobGallery(UUID jobId, UpdateGalleryDto request);

    List<GalleryImageDto> removeJobImage(UUID jobId, UUID imageId);

    IndividualJobDto createIndividualJob(CreateIndividualJobDto usersMeJobsPostRequestDto);

    CompanyJobDto createCorporateJob(UUID companyId, CreateCompanyJobDto companiesCompanyIdJobsPostRequestDto);

    Job getById(UUID jobId);

    JobDetailDto getJobDetail(UUID jobId, UUID viewerId);

    default JobDetailDto getJobDetail(UUID jobId) {
        return getJobDetail(jobId, null);
    }

    Job getDetailedById(UUID jobId);

    PageDto<JobSummaryDto> search(JobSearchCriteriaDto criteria, Pageable pageable, UUID viewerId);

    default PageDto<JobSummaryDto> search(JobSearchCriteriaDto criteria, Pageable pageable) {
        return search(criteria, pageable, null);
    }

    PageDto<JobSummaryDto> getJobsOfCurrentUser(Pageable pageable);

    PageDto<JobSummaryDto> getJobsOfCompany(UUID companyId, Pageable pageable);

    void closeJob(UUID jobId);

    void cancelJob(UUID jobId);

    boolean existsById(UUID jobId);

    long countJobsCreatedBy(UUID userId);

}
