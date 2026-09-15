package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminAssignmentMappingTest {

    private JobAssignmentMapper jobAssignmentMapper;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID employerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties();
        storage.setPublicUrl("https://cdn.example.com");
        MediaUrlResolver urls = new MediaUrlResolver(new StorageUrlResolver(storage,
                new AppProperties("http://localhost:8080", "http://localhost:3000")));

        AddressMapper addressMapper = new AddressMapperImpl();
        UserMapper userMapper = new UserMapperImpl(urls, addressMapper,
                new EducationMapperImpl(), new ExperienceMapperImpl());
        GalleryMapper galleryMapper = new GalleryMapperImpl(urls);
        CompanyMapper companyMapper = new CompanyMapperImpl(urls,
                new CompanyContactMapperImpl(userMapper), addressMapper, galleryMapper, userMapper);
        PricingDtoMapper pricingDtoMapper = new PricingDtoMapper();
        JobMapper jobMapper = new JobMapperImpl(userMapper, new JobCategoryMapperImpl(), addressMapper,
                companyMapper, galleryMapper, pricingDtoMapper, urls);

        jobAssignmentMapper = new JobAssignmentMapperImpl(jobMapper, userMapper, companyMapper, pricingDtoMapper,
                new MessagingAvailability(new MessagingProperties()), new CancellationMapperImpl());
    }

    private JobAssignment assignment() {
        User worker = new User();
        worker.setId(workerId);
        worker.setFirstName("Deneme");
        worker.setLastName("İşçi");

        User employer = new User();
        employer.setId(employerId);
        employer.setFirstName("Deneme");
        employer.setLastName("İşveren");

        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Deneme Şirketi");

        Job job = new Job();
        job.setId(jobId);
        job.setTitle("Depo görevlisi");
        job.setCreator(employer);
        job.setCompany(company);

        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);
        assignment.setJob(job);
        assignment.setWorker(worker);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setPaymentStatus(PaymentStatus.HELD_IN_ESCROW);
        assignment.setWorkHours(8);
        assignment.setPricingVersion("v1");
        assignment.setCurrency("TRY");
        assignment.setWorkerPayout(new BigDecimal("100.00"));
        assignment.setCommissionRate(new BigDecimal("0.0750"));
        assignment.setCommissionAmount(new BigDecimal("7.50"));
        assignment.setTotalCharged(new BigDecimal("107.50"));
        return assignment;
    }

    @Test
    void theAdminSummaryCarriesBothPartiesOfTheAssignment() {
        JobAssignment assignment = assignment();
        AdminAssignmentDto dto = jobAssignmentMapper.toAdminAssignmentDto(assignment, assignment.getWorker());

        assertEquals(assignmentId, dto.id());
        assertEquals(jobId, dto.jobId());
        assertEquals("Depo görevlisi", dto.jobTitle());
        assertEquals("Deneme Şirketi", dto.companyName());
        assertEquals(workerId, dto.worker().getId());
        assertEquals(employerId, dto.employer().getId());
    }

    @Test
    void theAdminSummaryCarriesThePaymentStateAndThePriceBreakdown() {
        JobAssignment assignment = assignment();
        AdminAssignmentDto dto = jobAssignmentMapper.toAdminAssignmentDto(assignment, assignment.getWorker());

        assertEquals(AssignmentStatus.COMPLETED, dto.status());
        assertEquals(PaymentStatus.HELD_IN_ESCROW, dto.paymentStatus());
        assertEquals(8, dto.workHours());
        assertEquals("100.00", dto.pricing().workerPayout());
        assertEquals("7.50", dto.pricing().serviceFeeAmount());
        assertEquals("107.50", dto.pricing().employerTotal());
        assertEquals("TRY", dto.pricing().currency());
    }

    @Test
    void anIndividualJobHasNoCompanyName() {
        JobAssignment assignment = assignment();
        assignment.getJob().setCompany(null);

        assertNull(jobAssignmentMapper.toAdminAssignmentDto(assignment, assignment.getWorker()).companyName());
    }

    @Test
    void aWorkerWhoDeletedTheirAccountLeavesTheRowWithANullWorkerAndItsJob() {
        JobAssignment assignment = assignment();

        AdminAssignmentDto dto = jobAssignmentMapper.toAdminAssignmentDto(assignment, null);

        assertNull(dto.worker());
        assertEquals(jobId, dto.jobId());
        assertEquals(employerId, dto.employer().getId());
    }
}
