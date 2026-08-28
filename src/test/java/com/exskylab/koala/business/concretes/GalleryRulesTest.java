package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.core.dtos.gallery.request.GalleryImageEntryDto;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.mappers.GalleryMapperImpl;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.dataAccess.CompanyImageDao;
import com.exskylab.koala.dataAccess.JobImageDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyImage;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class GalleryRulesTest {

    private CompanyImageDao companyImageDao;
    private JobImageDao jobImageDao;
    private ImageService imageService;
    private UploadProperties uploadProperties;
    private GalleryManager galleryManager;

    private final Company company = company();
    private final Job job = job(company);

    @BeforeEach
    void setUp() {
        companyImageDao = mock(CompanyImageDao.class);
        jobImageDao = mock(JobImageDao.class);
        imageService = mock(ImageService.class);
        uploadProperties = new UploadProperties();

        StorageUrlResolver storageUrlResolver = mock(StorageUrlResolver.class);
        when(storageUrlResolver.urlFor(any())).thenAnswer(call -> "https://cdn/" + call.getArgument(0));

        galleryManager = new GalleryManager(companyImageDao, jobImageDao, imageService,
                new GalleryMapperImpl(new MediaUrlResolver(storageUrlResolver)),
                new MediaUrlResolver(storageUrlResolver), uploadProperties);

        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId())).thenReturn(List.of());
        when(jobImageDao.findAllByJobIdOrderBySortOrderAsc(job.getId())).thenReturn(List.of());
    }

    @Test
    void firstUploadedImageBecomesTheCover() {
        Image uploaded = image();
        when(imageService.uploadImage(any())).thenReturn(uploaded);

        galleryManager.addCompanyImage(company, file());

        CompanyImage saved = captureCompanyImage();
        assertTrue(saved.isCover());
        assertEquals(0, saved.getSortOrder());
    }

    @Test
    void aSecondUploadDoesNotStealTheCover() {
        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId()))
                .thenReturn(List.of(companyImage(company, image(), 0, true)));
        when(companyImageDao.countByCompanyId(company.getId())).thenReturn(1L);
        when(imageService.uploadImage(any())).thenReturn(image());

        galleryManager.addCompanyImage(company, file());

        CompanyImage saved = captureCompanyImage();
        assertFalse(saved.isCover());
        assertEquals(1, saved.getSortOrder());
    }

    @Test
    void uploadingBeyondTheLimitIsRejected() {
        uploadProperties.setMaxCompanyImages(2);
        when(companyImageDao.countByCompanyId(company.getId())).thenReturn(2L);

        assertThrows(ConflictException.class, () -> galleryManager.addCompanyImage(company, file()));
        verify(imageService, never()).uploadImage(any());
    }

    @Test
    void aJobGalleryCanSelectAnImageFromItsCompanyGallery() {
        Image shared = image();
        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId()))
                .thenReturn(List.of(companyImage(company, shared, 0, true)));

        galleryManager.replaceJobGallery(job, gallery(entry(shared.getId(), 0, true)));

        List<JobImage> saved = captureJobImages();
        assertEquals(1, saved.size());
        assertEquals(shared.getId(), saved.getFirst().getImage().getId());
        assertTrue(saved.getFirst().isCover());
    }

    @Test
    void aJobGalleryCannotSelectAStrangersImage() {
        assertThrows(ResourceNotFoundException.class,
                () -> galleryManager.replaceJobGallery(job, gallery(entry(UUID.randomUUID(), 0, true))));
        verify(jobImageDao, never()).saveAll(anyList());
    }

    @Test
    void anIndividualJobHasNoCompanyGalleryToSelectFrom() {
        Job individualJob = job(null);
        when(jobImageDao.findAllByJobIdOrderBySortOrderAsc(individualJob.getId())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> galleryManager.replaceJobGallery(individualJob, gallery(entry(UUID.randomUUID(), 0, false))));
    }

    @Test
    void aGalleryWithoutAnExplicitCoverGetsOneAnyway() {
        Image first = image();
        Image second = image();
        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId()))
                .thenReturn(List.of(companyImage(company, first, 0, false),
                        companyImage(company, second, 1, false)));

        galleryManager.replaceCompanyGallery(company,
                gallery(entry(first.getId(), 0, false), entry(second.getId(), 1, false)));

        List<CompanyImage> saved = captureCompanyImages();
        assertEquals(1, saved.stream().filter(CompanyImage::isCover).count());
        assertTrue(saved.getFirst().isCover());
    }

    @Test
    void removingTheCoverPromotesTheNextImage() {
        Image cover = image();
        Image other = image();
        CompanyImage coverEntry = companyImage(company, cover, 0, true);
        CompanyImage otherEntry = companyImage(company, other, 1, false);

        when(companyImageDao.findByCompanyIdAndImageId(company.getId(), cover.getId()))
                .thenReturn(Optional.of(coverEntry));
        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId()))
                .thenReturn(new ArrayList<>(List.of(otherEntry)));

        List<GalleryImageDto> remaining = galleryManager.removeCompanyImage(company, cover.getId());

        verify(companyImageDao).delete(coverEntry);
        assertTrue(otherEntry.isCover());
        assertEquals(1, remaining.size());
    }

    @Test
    void removingAnImageThatIsNotInTheGalleryIsNotFound() {
        when(companyImageDao.findByCompanyIdAndImageId(any(), any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> galleryManager.removeCompanyImage(company, UUID.randomUUID()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void anImageDroppedFromTheRequestIsRemoved() {
        Image kept = image();
        Image dropped = image();
        CompanyImage keptEntry = companyImage(company, kept, 0, true);
        CompanyImage droppedEntry = companyImage(company, dropped, 1, false);

        when(companyImageDao.findAllByCompanyIdOrderBySortOrderAsc(company.getId()))
                .thenReturn(List.of(keptEntry, droppedEntry));

        galleryManager.replaceCompanyGallery(company, gallery(entry(kept.getId(), 0, true)));

        var captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(companyImageDao).deleteAll(captor.capture());
        assertEquals(List.of(droppedEntry), new ArrayList<>((java.util.Collection<CompanyImage>) captor.getValue()));
    }

    @Test
    void coverUrlsAreCollectedInASingleQuery() {
        JobImage cover = jobImage(job, image(), 0, true);
        when(jobImageDao.findAllByJobIdInAndCoverTrue(List.of(job.getId()))).thenReturn(List.of(cover));

        var covers = galleryManager.coverUrlsByJobIds(List.of(job.getId()));

        assertEquals(1, covers.size());
        assertTrue(covers.get(job.getId()).startsWith("https://cdn/"));
        verify(jobImageDao, times(1)).findAllByJobIdInAndCoverTrue(anyList());
    }

    @Test
    void theSameImageCannotBeListedTwice() {
        UUID imageId = UUID.randomUUID();
        assertFalse(gallery(entry(imageId, 0, true), entry(imageId, 1, false)).isEveryImageListedOnce());
    }

    @Test
    void onlyOneImageCanBeTheCover() {
        assertFalse(gallery(entry(UUID.randomUUID(), 0, true), entry(UUID.randomUUID(), 1, true))
                .isAtMostOneCoverSelected());
    }

    private CompanyImage captureCompanyImage() {
        var captor = org.mockito.ArgumentCaptor.forClass(CompanyImage.class);
        verify(companyImageDao).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<CompanyImage> captureCompanyImages() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(companyImageDao).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<JobImage> captureJobImages() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(jobImageDao).saveAll(captor.capture());
        return captor.getValue();
    }

    private static UpdateGalleryDto gallery(GalleryImageEntryDto... entries) {
        UpdateGalleryDto request = new UpdateGalleryDto();
        request.setImages(new ArrayList<>(List.of(entries)));
        return request;
    }

    private static GalleryImageEntryDto entry(UUID imageId, int sortOrder, boolean cover) {
        GalleryImageEntryDto entry = new GalleryImageEntryDto();
        entry.setImageId(imageId);
        entry.setSortOrder(sortOrder);
        entry.setCover(cover);
        return entry;
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    private static Image image() {
        Image image = new Image();
        image.setId(UUID.randomUUID());
        image.setFileUrl("images/" + UUID.randomUUID() + ".png");
        return image;
    }

    private static Company company() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        return company;
    }

    private static Job job(Company company) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCompany(company);
        return job;
    }

    private static CompanyImage companyImage(Company company, Image image, int sortOrder, boolean cover) {
        CompanyImage entry = new CompanyImage();
        entry.setId(UUID.randomUUID());
        entry.setCompany(company);
        entry.setImage(image);
        entry.setSortOrder(sortOrder);
        entry.setCover(cover);
        return entry;
    }

    private static JobImage jobImage(Job job, Image image, int sortOrder, boolean cover) {
        JobImage entry = new JobImage();
        entry.setId(UUID.randomUUID());
        entry.setJob(job);
        entry.setImage(image);
        entry.setSortOrder(sortOrder);
        entry.setCover(cover);
        return entry;
    }
}
