package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyType;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.JobCategory;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.Review;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperCharacterizationTest {

    private static final String PUBLIC_URL = "https://cdn.example.com";

    private AddressMapper addressMapper;
    private UserMapper userMapper;
    private ImageMapper imageMapper;
    private JobCategoryMapper jobCategoryMapper;
    private CompanyContactMapper companyContactMapper;
    private GalleryMapper galleryMapper;
    private CompanyMapper companyMapper;
    private JobMapper jobMapper;
    private JobAssignmentMapper jobAssignmentMapper;
    private JobApplicationMapper jobApplicationMapper;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties();
        storage.setPublicUrl(PUBLIC_URL);
        StorageUrlResolver urls0 = new StorageUrlResolver(storage,
                new AppProperties("http://localhost:8080", "http://localhost:3000"));

        MediaUrlResolver urls = new MediaUrlResolver(urls0);

        addressMapper = new AddressMapperImpl();
        jobCategoryMapper = new JobCategoryMapperImpl();
        imageMapper = new ImageMapperImpl(urls);
        userMapper = new UserMapperImpl(urls, addressMapper, new EducationMapperImpl(), new ExperienceMapperImpl());
        companyContactMapper = new CompanyContactMapperImpl(userMapper);
        galleryMapper = new GalleryMapperImpl(urls);
        companyMapper = new CompanyMapperImpl(urls, companyContactMapper, addressMapper, galleryMapper,
                userMapper);
        PricingDtoMapper pricingDtoMapper = new PricingDtoMapper();
        jobMapper = new JobMapperImpl(userMapper, jobCategoryMapper, addressMapper, companyMapper,
                galleryMapper, pricingDtoMapper, urls);
        jobAssignmentMapper = new JobAssignmentMapperImpl(jobMapper, userMapper, companyMapper,
                pricingDtoMapper, new MessagingAvailability(new MessagingProperties()), new CancellationMapperImpl());
        jobApplicationMapper = new JobApplicationMapperImpl(userMapper, jobMapper, jobAssignmentMapper, urls);
    }

    @Test
    void addressIsFlattenedAcrossCityCountryAndDistrict() {
        var dto = addressMapper.toAddressDto(address());

        assertEquals("Turkiye", dto.getCountryName());
        assertEquals("TR", dto.getCountryCode());
        assertEquals("Istanbul", dto.getCityName());
        assertEquals("Kadikoy", dto.getDistrictName());
        assertEquals("Moda", dto.getComponents().getNeighborhood());
        assertEquals("Moda Caddesi", dto.getComponents().getThoroughfare());
        assertEquals("1", dto.getComponents().getBuildingNumber());
        assertEquals("34710", dto.getComponents().getPostalCode());
    }

    @Test
    void profilePictureBecomesAnAbsoluteUrl() {
        User user = user();
        user.setProfilePicture(image());

        assertEquals(PUBLIC_URL + "/images/abc-foto.png", userMapper.toSafeUserDto(user).getProfilePictureUrl());
        assertEquals(PUBLIC_URL + "/images/abc-foto.png", userMapper.toUserMeResponseDto(user, null, null, null).getProfilePictureUrl());
    }

    @Test
    void aUserWithoutAProfilePictureGetsANullUrl() {
        User user = user();
        user.setProfilePicture(null);

        assertNull(userMapper.toSafeUserDto(user).getProfilePictureUrl());
        assertNull(userMapper.toUserMeResponseDto(user, null, null, null).getProfilePictureUrl());
    }

    @Test
    void everyMapperReturnsNullForNullInput() {
        assertNull(addressMapper.toAddressDto(null));
        assertNull(userMapper.toSafeUserDto(null));
        assertNull(userMapper.toUserMeResponseDto(null, null, null, null));
        assertNull(jobCategoryMapper.toCategoryDto(null));
        assertNull(imageMapper.toUploadImageResponseDto(null));
        assertNull(companyMapper.toOnlyCompanyDto(null));
        assertNull(companyMapper.toCompanyDto(null));
        assertNull(jobMapper.toShortJobDto(null));
    }

    @Test
    void anIndividualJobAssignmentMapsWithoutACompany() {
        JobAssignment assignment = assignment(individualJob());

        var dto = assertDoesNotThrow(
                () -> jobAssignmentMapper.toMyAssignmentDto(assignment, null, null, null, employerFacts(), null));

        assertNotNull(dto);
        assertNull(dto.company());
        assertNull(dto.job().getCompanyLogoUrl());
        assertNotNull(dto.job());
        assertNotNull(dto.worker());
        assertNotNull(dto.employer());
        assertNull(dto.application());
        assertNull(dto.cancellation());
        assertNull(dto.payout());
        assertNull(dto.paymentSecuredAt());
        assertNull(dto.myReview());
        assertFalse(dto.counterpartyReviewed());
    }

    @Test
    void theViewerFactsDecideTheReviewStateAndWhoSeesThePricingBreakdown() {
        JobAssignment assignment = pricedAssignment(individualJob());
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setJobAssignment(assignment);
        review.setAuthor(assignment.getWorker());
        review.setSubject(assignment.getJob().getCreator());
        review.setRating(4);
        review.setComment("Dakik");
        review.setCreatedAt(Instant.parse("2026-09-14T10:00:00Z"));

        var employerDto = jobAssignmentMapper.toMyAssignmentDto(assignment, null, null, null,
                new JobAssignmentMapper.ViewerFacts(false, null, true), null);
        var workerDto = jobAssignmentMapper.toMyAssignmentDto(assignment, null, null, null,
                new JobAssignmentMapper.ViewerFacts(true, review, false), null);

        assertEquals(new BigDecimal("110.00"), employerDto.totalCharged());
        assertEquals("v1", employerDto.pricing().pricingVersion());
        assertEquals("100.00", employerDto.pricing().workerPayout());
        assertEquals("0.1000", employerDto.pricing().serviceFeeRate());
        assertEquals("10.00", employerDto.pricing().serviceFeeAmount());
        assertEquals("110.00", employerDto.pricing().employerTotal());
        assertNull(employerDto.myReview());
        assertTrue(employerDto.counterpartyReviewed());

        assertNull(workerDto.totalCharged());
        assertNull(workerDto.pricing());
        assertEquals(new BigDecimal("100.00"), workerDto.workerPayout());
        assertEquals(review.getId(), workerDto.myReview().id());
        assertEquals(4, workerDto.myReview().rating());
        assertEquals("Dakik", workerDto.myReview().comment());
        assertEquals(review.getCreatedAt(), workerDto.myReview().createdAt());
        assertFalse(workerDto.counterpartyReviewed());
    }

    @Test
    void aCompanyJobAssignmentCarriesTheCompany() {
        JobAssignment assignment = assignment(companyJob());

        assignment.getJob().getCompany().setLogo(image());

        var dto = jobAssignmentMapper.toMyAssignmentDto(assignment, null, null, null, employerFacts(), null);

        assertNotNull(dto.company());
        assertEquals("Akme AS", dto.company().name());
        assertEquals(assignment.getJob().getCompany().getId(), dto.company().id());
        assertEquals(PUBLIC_URL + "/images/abc-foto.png", dto.company().logoUrl());
        assertEquals(PUBLIC_URL + "/images/abc-foto.png", dto.job().getCompanyLogoUrl());
    }

    @Test
    void jobIsMappedWithNestedCreatorCategoryAndAddress() {
        var dto = jobMapper.toUsersMeJobsPostResponseDto(individualJob());

        assertEquals("Garson", dto.getTitle());
        assertEquals(JobEmployerType.INDIVIDUAL, dto.getEmployerType());
        assertNotNull(dto.getCreator());
        assertNotNull(dto.getCategory());
        assertNotNull(dto.getAddress());
        assertEquals("Istanbul", dto.getAddress().getCityName());
    }

    @Test
    void anApplicationWithoutAnAssignmentLeavesTheAssignmentNull() {
        JobApplication application = application();

        var dto = jobApplicationMapper.toJobApplicationsApplicationIdPatchResponseDto(application);

        assertNull(dto.getAssignment());
        assertNotNull(dto.getApplicant());
        assertNotNull(dto.getJob());
    }

    @Test
    void anApplicationWithAnAssignmentCarriesTheShortAssignment() {
        JobApplication application = application();
        application.setAssignment(assignment(individualJob()));

        var dto = jobApplicationMapper.toJobApplicationsApplicationIdPatchResponseDto(application);

        assertNotNull(dto.getAssignment());
        assertEquals(AssignmentStatus.PENDING_CONFIRMATION, dto.getAssignment().getStatus());
    }

    @Test
    void aUsersAddressIsMappedIntoTheProfileResponse() {
        User user = user();
        user.setAddress(address());

        var dto = userMapper.toUserMeResponseDto(user, null, null, null);

        assertNotNull(dto.getAddress());
        assertEquals("Istanbul", dto.getAddress().getCityName());
        assertEquals("Kadikoy", dto.getAddress().getDistrictName());
        assertEquals("ada@example.com", dto.getEmail());
    }

    @Test
    void imageUploadResponseCarriesTheAbsoluteUrl() {
        var dto = imageMapper.toUploadImageResponseDto(image());

        assertEquals(PUBLIC_URL + "/images/abc-foto.png", dto.getFileUrl());
        assertEquals("foto.png", dto.getFileName());
        assertEquals(1024L, dto.getFileSize());
    }

    @Test
    void theProfileResponseTellsTheClientWhetherTheUserIsAnEmployer() {
        User employer = user();
        employer.setEmployer(true);
        employer.setEmailVerified(true);
        employer.setAuthorities(java.util.Set.of(com.exskylab.koala.entities.Role.ROLE_USER));

        var dto = userMapper.toUserMeResponseDto(employer, null, null, null);

        assertTrue(dto.isEmployer(), "Router guard bu alana bagli; eksik olamaz.");
        assertTrue(dto.isEmailVerified());
        assertNotNull(dto.getRoles());
        assertTrue(dto.getRoles().contains(com.exskylab.koala.entities.Role.ROLE_USER));
    }

    @Test
    void aWorkerIsNotReportedAsAnEmployer() {
        assertFalse(userMapper.toUserMeResponseDto(user(), null, null, null).isEmployer());
    }

    @Test
    void anUnprotectedSavedJobSummaryDefaultsToApproximateCoordinates() {
        var dto = jobMapper.toJobSummaryDto(individualJob());

        assertEquals(40.99, dto.getLatitude());
        assertEquals(29.03, dto.getLongitude());
        assertEquals(AddressPrecision.APPROXIMATE, dto.getAddressPrecision());
        assertEquals("Istanbul", dto.getCityName());
    }

    @Test
    void aJobSummaryCarriesThePersistedRevisionTime() {
        Job job = individualJob();
        Instant revisionTime = Instant.parse("2026-09-09T10:11:12Z");
        job.setUpdatedAt(revisionTime);

        var dto = jobMapper.toJobSummaryDto(job);

        assertEquals(revisionTime, dto.getUpdatedAt());
    }

    @Test
    void nestedApplicationAndAssignmentSummariesAlsoDefaultToApproximateCoordinates() {
        var application = jobApplicationMapper.toMyApplicationDto(application());
        var assignment = jobAssignmentMapper.toMyAssignmentDto(assignment(individualJob()), null, null, null, workerFacts(), null);

        assertEquals(40.99, application.job().getLatitude());
        assertEquals(AddressPrecision.APPROXIMATE, application.job().getAddressPrecision());
        assertEquals(40.99, assignment.job().getLatitude());
        assertEquals(AddressPrecision.APPROXIMATE, assignment.job().getAddressPrecision());
    }

    @Test
    void theJobDetailCarriesEverythingTheDetailPageNeeds() {
        Job job = individualJob();
        job.setDescription("Hafta sonu servis destegi");
        job.setDuties("Masa hazirligi");
        job.setDressCode("Siyah pantolon");
        job.setMinAge(18);
        job.setMaxAge(40);
        job.setInsuranceRequired(true);
        job.setExperienceRequired(false);

        var dto = jobMapper.toJobDetailDto(job, JobMapper.ViewerFacts.EMPLOYER);

        assertEquals("Hafta sonu servis destegi", dto.getDescription());
        assertEquals("Masa hazirligi", dto.getDuties());
        assertEquals("Siyah pantolon", dto.getDressCode());
        assertEquals(18, dto.getMinAge());
        assertEquals(40, dto.getMaxAge());
        assertEquals(Boolean.TRUE, dto.getInsuranceRequired());
        assertEquals(Boolean.FALSE, dto.getExperienceRequired());
        assertNotNull(dto.getAddress());
        assertEquals("Kadikoy", dto.getAddress().getDistrictName());
        assertNotNull(dto.getCategory());
    }

    @Test
    void aCompanyJobSummaryCarriesTheCompanyIdAndAnIndividualJobDoesNot() {
        Job companyJob = companyJob();
        UUID companyId = companyJob.getCompany().getId();

        var companySummary = jobMapper.toJobSummaryDto(companyJob);
        var individualSummary = jobMapper.toJobSummaryDto(individualJob());

        assertEquals(companyId, companySummary.getCompanyId());
        assertEquals("Akme AS", companySummary.getCompanyName());
        assertNull(individualSummary.getCompanyId());
        assertNull(individualSummary.getCompanyName());
    }

    @Test
    void theEmployerViewOfAJobCarriesTheFullPricingBreakdown() {
        var summary = jobMapper.toJobSummaryDto(pricedJob(), JobMapper.ViewerFacts.EMPLOYER);
        var detail = jobMapper.toJobDetailDto(pricedJob(), JobMapper.ViewerFacts.EMPLOYER);

        for (var pricing : List.of(summary.getPricing(), detail.getPricing())) {
            assertEquals("v1", pricing.pricingVersion());
            assertEquals("TRY", pricing.currency());
            assertEquals("100.00", pricing.workerPayout());
            assertEquals("0.1000", pricing.serviceFeeRate());
            assertEquals("10.00", pricing.serviceFeeAmount());
            assertEquals("110.00", pricing.employerTotal());
        }
    }

    @Test
    void thePublicViewOfAJobKeepsOnlyTheWorkerPayout() {
        var summary = jobMapper.toJobSummaryDto(pricedJob(), JobMapper.ViewerFacts.PUBLIC);
        var detail = jobMapper.toJobDetailDto(pricedJob(), JobMapper.ViewerFacts.PUBLIC);

        for (var pricing : List.of(summary.getPricing(), detail.getPricing())) {
            assertEquals("v1", pricing.pricingVersion());
            assertEquals("TRY", pricing.currency());
            assertEquals("100.00", pricing.workerPayout());
            assertNull(pricing.serviceFeeRate());
            assertNull(pricing.serviceFeeAmount());
            assertNull(pricing.employerTotal());
        }
    }

    @Test
    void nestedJobSummariesAndSavedJobsDefaultToTheWorkerPricingView() {
        var assignment = jobAssignmentMapper.toMyAssignmentDto(pricedAssignment(pricedJob()), null, null, null,
                employerFacts(), null);
        var saved = jobMapper.toJobSummaryDtoList(List.of(pricedJob())).get(0);

        assertEquals("100.00", assignment.job().getPricing().workerPayout());
        assertNull(assignment.job().getPricing().employerTotal(),
                "Atamanin icindeki ilan ozeti herkese acik JobSummaryDto ile ayni sekli tasir; "
                        + "isveren tam dokumu atamanin kendi pricing alanindan okur.");
        assertEquals("110.00", assignment.pricing().employerTotal());
        assertNull(saved.getPricing().employerTotal());
    }

    @Test
    void aJobWithoutAPricingSnapshotHasNoPricingInEitherView() {
        assertNull(jobMapper.toJobSummaryDto(individualJob(), JobMapper.ViewerFacts.PUBLIC).getPricing());
        assertNull(jobMapper.toJobDetailDto(individualJob(), JobMapper.ViewerFacts.EMPLOYER).getPricing());
    }

    @Test
    void theCategoryExposesAStableCodeNotJustItsName() {
        var dto = jobMapper.toJobSummaryDto(individualJob());

        assertEquals("SERVIS", dto.getCategory().getCode(),
                "Istemci ikon/renk eslemesini UUID'ye degil koda baglayabilmeli.");
    }

    @Test
    void companyContactsAreMappedAsAList() {
        Company company = company();
        var dto = companyMapper.toCompanyDto(company);

        assertEquals("Akme AS", dto.getName());
        assertNotNull(dto.getCompanyContacts());
        assertEquals(0, dto.getCompanyContacts().size());
    }

    private Address address() {
        Country country = new Country();
        country.setId(UUID.randomUUID());
        country.setName("Turkiye");
        country.setCode("TR");

        City city = new City();
        city.setId(UUID.randomUUID());
        city.setName("Istanbul");
        city.setCountry(country);

        District district = new District();
        district.setId(UUID.randomUUID());
        district.setName("Kadikoy");
        district.setCity(city);

        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setCity(city);
        address.setDistrict(district);
        address.setCountry(country);
        address.setNeighborhood("Moda");
        address.setThoroughfare("Moda Caddesi");
        address.setBuildingNumber("1");
        address.setPostalCode("34710");
        address.setLatitude(new BigDecimal("40.987654"));
        address.setLongitude(new BigDecimal("29.034567"));
        return address;
    }

    private Image image() {
        Image image = new Image();
        image.setId(UUID.randomUUID());
        image.setFileName("foto.png");
        image.setFileType("image/png");
        image.setFileUrl("images/abc-foto.png");
        image.setFileSize(1024L);
        return image;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail("ada@example.com");
        return user;
    }

    private JobCategory category() {
        JobCategory category = new JobCategory();
        category.setId(UUID.randomUUID());
        category.setName("Servis");
        category.setRequiresInsurance(false);
        category.setCode("SERVIS");
        return category;
    }

    private Company company() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Akme AS");
        company.setEmail("info@akme.com");
        company.setType(CompanyType.LIMITED);
        company.setContacts(new ArrayList<>());
        return company;
    }

    private Job individualJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setCreator(user());
        job.setCategory(category());
        job.setAddress(address());
        job.setTitle("Garson");
        job.setCompany(null);
        return job;
    }

    private Job pricedJob() {
        Job job = individualJob();
        job.setPricingVersion("v1");
        job.setCurrency("TRY");
        job.setWorkerPayout(new BigDecimal("100.00"));
        job.setCommissionRate(new BigDecimal("0.1000"));
        job.setCommissionAmount(new BigDecimal("10.00"));
        job.setTotalCharged(new BigDecimal("110.00"));
        return job;
    }

    private Job companyJob() {
        Job job = individualJob();
        job.setEmployerType(JobEmployerType.COMPANY);
        job.setCompany(company());
        return job;
    }

    private JobAssignmentMapper.ViewerFacts employerFacts() {
        return new JobAssignmentMapper.ViewerFacts(false, null, false);
    }

    private JobAssignmentMapper.ViewerFacts workerFacts() {
        return new JobAssignmentMapper.ViewerFacts(true, null, false);
    }

    private JobAssignment pricedAssignment(Job job) {
        JobAssignment assignment = assignment(job);
        assignment.setPricingVersion("v1");
        assignment.setCurrency("TRY");
        assignment.setWorkerPayout(new BigDecimal("100.00"));
        assignment.setCommissionRate(new BigDecimal("0.1000"));
        assignment.setCommissionAmount(new BigDecimal("10.00"));
        assignment.setTotalCharged(new BigDecimal("110.00"));
        return assignment;
    }

    private JobAssignment assignment(Job job) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(user());
        assignment.setStatus(AssignmentStatus.PENDING_CONFIRMATION);
        assignment.setAssignmentDateTime(Instant.now());
        return assignment;
    }

    private JobApplication application() {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(individualJob());
        application.setUser(user());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setApplicationDateTime(Instant.now());
        application.setNotes("Musaitim");
        return application;
    }
}
