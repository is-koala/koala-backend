package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Job;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GalleryService {

    List<GalleryImageDto> getCompanyGallery(UUID companyId);

    List<GalleryImageDto> addCompanyImage(Company company, MultipartFile file);

    List<GalleryImageDto> replaceCompanyGallery(Company company, UpdateGalleryDto request);

    List<GalleryImageDto> removeCompanyImage(Company company, UUID imageId);

    List<GalleryImageDto> getJobGallery(UUID jobId);

    List<GalleryImageDto> addJobImage(Job job, MultipartFile file);

    List<GalleryImageDto> replaceJobGallery(Job job, UpdateGalleryDto request);

    List<GalleryImageDto> removeJobImage(Job job, UUID imageId);

    Map<UUID, String> coverUrlsByJobIds(Collection<UUID> jobIds);

    void fillJobCovers(List<JobSummaryDto> jobs);

    void removeImageEverywhere(UUID imageId);

    Image getCompanyImageForStaffDownload(UUID companyId, UUID imageId);
}
