package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.dtos.gallery.request.GalleryImageEntryDto;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.mappers.GalleryMapper;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.dataAccess.CompanyImageDao;
import com.exskylab.koala.dataAccess.JobImageDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyImage;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GalleryManager implements GalleryService {

    private final CompanyImageDao companyImageDao;
    private final JobImageDao jobImageDao;
    private final ImageService imageService;
    private final GalleryMapper galleryMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final UploadProperties uploadProperties;

    public GalleryManager(CompanyImageDao companyImageDao, JobImageDao jobImageDao,
                          ImageService imageService, GalleryMapper galleryMapper,
                          MediaUrlResolver mediaUrlResolver, UploadProperties uploadProperties) {
        this.companyImageDao = companyImageDao;
        this.jobImageDao = jobImageDao;
        this.imageService = imageService;
        this.galleryMapper = galleryMapper;
        this.mediaUrlResolver = mediaUrlResolver;
        this.uploadProperties = uploadProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GalleryImageDto> getCompanyGallery(UUID companyId) {
        log.debug("Listing gallery of company {}", companyId);
        return galleryMapper.toCompanyGalleryDtoList(
                companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(companyId));
    }

    @Override
    @Transactional
    public List<GalleryImageDto> addCompanyImage(Company company, MultipartFile file) {
        if (companyImageDao.countByCompanyId(company.getId()) >= uploadProperties.getMaxCompanyImages()) {
            throw new ConflictException(GalleryMessages.LIMIT_EXCEEDED);
        }

        Image image = imageService.uploadImage(file);

        List<CompanyImage> current = companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId());

        CompanyImage entry = new CompanyImage();
        entry.setCompany(company);
        entry.setImage(image);
        entry.setSortOrder(current.size());
        entry.setCover(current.isEmpty());

        companyImageDao.save(entry);
        log.info("Image {} added to gallery of company {}", image.getId(), company.getId());

        return getCompanyGallery(company.getId());
    }

    @Override
    @Transactional
    public List<GalleryImageDto> replaceCompanyGallery(Company company, UpdateGalleryDto request) {
        List<CompanyImage> current = companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId());
        Map<UUID, CompanyImage> byImageId = current.stream()
                .collect(Collectors.toMap(entry -> entry.getImage().getId(), entry -> entry,
                        (first, second) -> first, LinkedHashMap::new));

        List<GalleryImageEntryDto> requested = requestedEntries(request, byImageId.keySet());

        List<CompanyImage> kept = new ArrayList<>();
        for (GalleryImageEntryDto entry : requested) {
            CompanyImage existing = byImageId.remove(entry.getImageId());
            existing.setSortOrder(entry.getSortOrder());
            existing.setCover(Boolean.TRUE.equals(entry.getCover()));
            existing.setCaption(entry.getCaption());
            kept.add(existing);
        }

        applyDefaultCover(kept, CompanyImage::isCover, CompanyImage::setCover);
        companyImageDao.saveAll(kept);
        companyImageDao.deleteAll(byImageId.values());

        log.info("Gallery of company {} replaced with {} image(s)", company.getId(), kept.size());
        return getCompanyGallery(company.getId());
    }

    @Override
    @Transactional
    public List<GalleryImageDto> removeCompanyImage(Company company, UUID imageId) {
        CompanyImage entry = companyImageDao.findByCompanyIdAndImageId(company.getId(), imageId)
                .orElseThrow(() -> new ResourceNotFoundException(GalleryMessages.IMAGE_NOT_IN_GALLERY));

        companyImageDao.delete(entry);
        log.info("Image {} removed from gallery of company {}", imageId, company.getId());

        List<CompanyImage> remaining = companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId());
        applyDefaultCover(remaining, CompanyImage::isCover, CompanyImage::setCover);
        companyImageDao.saveAll(remaining);

        return galleryMapper.toCompanyGalleryDtoList(remaining);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GalleryImageDto> getJobGallery(UUID jobId) {
        log.debug("Listing gallery of job {}", jobId);
        return galleryMapper.toJobGalleryDtoList(jobImageDao.findAllByJobIdOrderBySortOrderAsc(jobId));
    }

    @Override
    @Transactional
    public List<GalleryImageDto> addJobImage(Job job, MultipartFile file) {
        if (jobImageDao.countByJobId(job.getId()) >= uploadProperties.getMaxJobImages()) {
            throw new ConflictException(GalleryMessages.LIMIT_EXCEEDED);
        }

        Image image = imageService.uploadImage(file);

        List<JobImage> current = jobImageDao.findAllByJobIdOrderBySortOrderAsc(job.getId());

        JobImage entry = new JobImage();
        entry.setJob(job);
        entry.setImage(image);
        entry.setSortOrder(current.size());
        entry.setCover(current.isEmpty());

        jobImageDao.save(entry);
        log.info("Image {} added to gallery of job {}", image.getId(), job.getId());

        return getJobGallery(job.getId());
    }

    @Override
    @Transactional
    public List<GalleryImageDto> replaceJobGallery(Job job, UpdateGalleryDto request) {
        List<JobImage> current = jobImageDao.findAllByJobIdOrderBySortOrderAsc(job.getId());
        Map<UUID, JobImage> byImageId = current.stream()
                .collect(Collectors.toMap(entry -> entry.getImage().getId(), entry -> entry,
                        (first, second) -> first, LinkedHashMap::new));

        Map<UUID, Image> selectable = new HashMap<>();
        byImageId.forEach((imageId, entry) -> selectable.put(imageId, entry.getImage()));
        if (job.getCompany() != null) {
            companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(job.getCompany().getId())
                    .forEach(entry -> selectable.putIfAbsent(entry.getImage().getId(), entry.getImage()));
        }

        List<GalleryImageEntryDto> requested = requestedEntries(request, selectable.keySet());

        List<JobImage> kept = new ArrayList<>();
        for (GalleryImageEntryDto requestedEntry : requested) {
            JobImage entry = byImageId.remove(requestedEntry.getImageId());
            if (entry == null) {
                entry = new JobImage();
                entry.setJob(job);
                entry.setImage(selectable.get(requestedEntry.getImageId()));
            }
            entry.setSortOrder(requestedEntry.getSortOrder());
            entry.setCover(Boolean.TRUE.equals(requestedEntry.getCover()));
            entry.setCaption(requestedEntry.getCaption());
            kept.add(entry);
        }

        applyDefaultCover(kept, JobImage::isCover, JobImage::setCover);
        jobImageDao.saveAll(kept);
        jobImageDao.deleteAll(byImageId.values());

        log.info("Gallery of job {} replaced with {} image(s)", job.getId(), kept.size());
        return getJobGallery(job.getId());
    }

    @Override
    @Transactional
    public List<GalleryImageDto> removeJobImage(Job job, UUID imageId) {
        List<JobImage> current = jobImageDao.findAllByJobIdOrderBySortOrderAsc(job.getId());
        JobImage entry = current.stream()
                .filter(candidate -> candidate.getImage().getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(GalleryMessages.IMAGE_NOT_IN_GALLERY));

        jobImageDao.delete(entry);
        current.remove(entry);
        log.info("Image {} removed from gallery of job {}", imageId, job.getId());

        applyDefaultCover(current, JobImage::isCover, JobImage::setCover);
        jobImageDao.saveAll(current);

        return galleryMapper.toJobGalleryDtoList(current);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> coverUrlsByJobIds(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> covers = new HashMap<>();
        for (JobImage entry : jobImageDao.findAllByJobIdInAndCoverTrue(jobIds)) {
            covers.putIfAbsent(entry.getJob().getId(), mediaUrlResolver.toPublicUrl(entry.getImage()));
        }
        return covers;
    }

    @Override
    @Transactional(readOnly = true)
    public void fillJobCovers(List<JobSummaryDto> jobs) {
        List<JobSummaryDto> present = jobs.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return;
        }

        Map<UUID, String> covers = coverUrlsByJobIds(present.stream().map(JobSummaryDto::getId).toList());
        present.forEach(job -> job.setCoverImageUrl(covers.get(job.getId())));
    }

    @Override
    @Transactional
    public void removeImageEverywhere(UUID imageId) {
        List<JobImage> jobEntries = jobImageDao.findAllByImageId(imageId);
        jobImageDao.deleteAll(jobEntries);

        List<CompanyImage> companyEntries = companyImageDao.findAllByImageId(imageId);
        companyImageDao.deleteAll(companyEntries);

        imageService.deleteImage(imageId);
        log.info("Image {} removed from {} job and {} company gallery entries",
                imageId, jobEntries.size(), companyEntries.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Image getCompanyImageForStaffDownload(UUID companyId, UUID imageId) {
        CompanyImage entry = companyImageDao.findByCompanyIdAndImageId(companyId, imageId)
                .orElseThrow(() -> new ResourceNotFoundException(GalleryMessages.IMAGE_NOT_IN_GALLERY));
        return entry.getImage();
    }

    private List<GalleryImageEntryDto> requestedEntries(UpdateGalleryDto request, Set<UUID> selectableImageIds) {
        List<GalleryImageEntryDto> requested = request.getImages();

        for (GalleryImageEntryDto entry : requested) {
            if (!selectableImageIds.contains(entry.getImageId())) {
                throw new ResourceNotFoundException(GalleryMessages.IMAGE_NOT_IN_GALLERY);
            }
        }
        return requested;
    }

    private <T> void applyDefaultCover(List<T> entries, java.util.function.Predicate<T> isCover,
                                       java.util.function.BiConsumer<T, Boolean> setCover) {
        if (entries.isEmpty()) {
            return;
        }
        if (entries.stream().anyMatch(isCover)) {
            return;
        }
        setCover.accept(entries.getFirst(), true);
    }
}
