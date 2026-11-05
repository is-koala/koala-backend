package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.address.response.AddressPositionDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobAddressVisibilityTest {

    private JobAddressVisibility visibility;
    private Job job;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        visibility = new JobAddressVisibility();
        ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);
        job = new Job();
        job.setCreator(owner);
    }

    @Test
    void anAnonymousViewerReceivesOnlyAnApproximateAddress() {
        JobDetailDto detail = detail();

        visibility.protect(job, detail, null, false);

        assertEquals(AddressPrecision.APPROXIMATE, detail.getAddressPrecision());
        assertEquals("Moda", detail.getAddress().getComponents().getNeighborhood());
        assertNull(detail.getAddress().getComponents().getThoroughfare());
        assertNull(detail.getAddress().getComponents().getBuildingNumber());
        assertEquals(new BigDecimal("40.99"), detail.getAddress().getPosition().getLatitude());
        assertEquals(new BigDecimal("29.03"), detail.getAddress().getPosition().getLongitude());
    }

    @Test
    void anUnrelatedViewerReceivesOnlyAnApproximateAddress() {
        JobDetailDto detail = detail();

        visibility.protect(job, detail, UUID.randomUUID(), false);

        assertEquals(AddressPrecision.APPROXIMATE, detail.getAddressPrecision());
        assertNull(detail.getAddress().getComponents().getUnitNumber());
    }

    @Test
    void theJobOwnerReceivesTheExactAddress() {
        JobDetailDto detail = detail();

        visibility.protect(job, detail, ownerId, false);

        assertEquals(AddressPrecision.EXACT, detail.getAddressPrecision());
        assertEquals("Caferağa Sokak", detail.getAddress().getComponents().getThoroughfare());
        assertEquals(new BigDecimal("40.987654"), detail.getAddress().getPosition().getLatitude());
    }

    @Test
    void anAssignedWorkerReceivesTheExactAddress() {
        JobDetailDto detail = detail();

        visibility.protect(job, detail, UUID.randomUUID(), true);

        assertEquals(AddressPrecision.EXACT, detail.getAddressPrecision());
        assertEquals("12", detail.getAddress().getComponents().getBuildingNumber());
    }

    @Test
    void aPublicCompanyJobRevealsTheStreetLevelAddressButNotTheUnitToEveryone() {
        job.setAddressPublic(true);
        job.setEmployerType(JobEmployerType.COMPANY);
        JobDetailDto detail = detail();

        visibility.protect(job, detail, null, false);

        assertEquals(AddressPrecision.EXACT, detail.getAddressPrecision());
        assertEquals("Caferağa Sokak", detail.getAddress().getComponents().getThoroughfare());
        assertEquals("12", detail.getAddress().getComponents().getBuildingNumber());
        assertEquals("34710", detail.getAddress().getComponents().getPostalCode());
        assertEquals(new BigDecimal("40.987654"), detail.getAddress().getPosition().getLatitude());
        assertNull(detail.getAddress().getComponents().getFloor());
        assertNull(detail.getAddress().getComponents().getUnitNumber());
        assertNull(detail.getAddress().getComponents().getAdditionalDirections());
        assertEquals("Moda, Caferağa Sokak, No: 12, Kadıköy/İstanbul, 34710", detail.getAddress().getDisplayAddress());
    }

    @Test
    void theOwnerAndTheAssignedWorkerOfAPublicCompanyJobStillReceiveTheUnit() {
        job.setAddressPublic(true);
        job.setEmployerType(JobEmployerType.COMPANY);
        JobDetailDto forTheOwner = detail();
        JobDetailDto forTheWorker = detail();

        visibility.protect(job, forTheOwner, ownerId, false);
        visibility.protect(job, forTheWorker, UUID.randomUUID(), true);

        for (JobDetailDto detail : new JobDetailDto[]{forTheOwner, forTheWorker}) {
            assertEquals(AddressPrecision.EXACT, detail.getAddressPrecision());
            assertEquals("3", detail.getAddress().getComponents().getFloor());
            assertEquals("4", detail.getAddress().getComponents().getUnitNumber());
            assertEquals("Zil çalışmıyor", detail.getAddress().getComponents().getAdditionalDirections());
            assertEquals(DISPLAY, detail.getAddress().getDisplayAddress());
        }
    }

    @Test
    void theListingRuleDoesNotLetAnAssignmentRevealAHiddenAddress() {
        JobSummaryDto summary = approximateSummary();

        visibility.protectListing(job, summary, UUID.randomUUID());

        assertEquals(AddressPrecision.APPROXIMATE, summary.getAddressPrecision());
        assertEquals(40.99, summary.getLatitude());
        assertEquals(29.03, summary.getLongitude());
    }

    @Test
    void theListingRuleRevealsAPublicCompanyAddressAndTheOwnersOwnJob() {
        JobSummaryDto forTheOwner = approximateSummary();
        visibility.protectListing(job, forTheOwner, ownerId);

        job.setAddressPublic(true);
        job.setEmployerType(JobEmployerType.COMPANY);
        JobSummaryDto forAnyone = approximateSummary();
        visibility.protectListing(job, forAnyone, null);

        for (JobSummaryDto summary : new JobSummaryDto[]{forTheOwner, forAnyone}) {
            assertEquals(AddressPrecision.EXACT, summary.getAddressPrecision());
            assertEquals(40.987654, summary.getLatitude());
            assertEquals(29.034567, summary.getLongitude());
        }
    }

    @Test
    void thePersonalSurfacesStillLetAnActiveAssignmentRevealTheAddress() {
        JobSummaryDto summary = approximateSummary();

        visibility.protect(job, summary, UUID.randomUUID(), true);

        assertEquals(AddressPrecision.EXACT, summary.getAddressPrecision());
        assertEquals(40.987654, summary.getLatitude());
    }

    @Test
    void anIndividualJobCannotRevealItsExactAddressToEveryone() {
        job.setAddressPublic(true);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        JobDetailDto detail = detail();

        visibility.protect(job, detail, null, false);

        assertEquals(AddressPrecision.APPROXIMATE, detail.getAddressPrecision());
        assertNull(detail.getAddress().getComponents().getUnitNumber());
    }

    @Test
    void anAuthorizedSummaryRestoresExactCoordinatesFromTheJob() {
        Address address = new Address();
        address.setLatitude(new BigDecimal("40.987654"));
        address.setLongitude(new BigDecimal("29.034567"));
        job.setAddress(address);
        JobSummaryDto summary = new JobSummaryDto();
        summary.setLatitude(40.99);
        summary.setLongitude(29.03);
        summary.setAddressPrecision(AddressPrecision.APPROXIMATE);

        visibility.protect(job, summary, ownerId, false);

        assertEquals(AddressPrecision.EXACT, summary.getAddressPrecision());
        assertEquals(40.987654, summary.getLatitude());
        assertEquals(29.034567, summary.getLongitude());
    }

    private static final String DISPLAY = "Moda, Caferağa Sokak, No: 12, Kat: 3, Daire: 4, Kadıköy/İstanbul, 34710";

    private JobSummaryDto approximateSummary() {
        Address address = new Address();
        address.setLatitude(new BigDecimal("40.987654"));
        address.setLongitude(new BigDecimal("29.034567"));
        job.setAddress(address);
        JobSummaryDto summary = new JobSummaryDto();
        summary.setLatitude(40.99);
        summary.setLongitude(29.03);
        summary.setAddressPrecision(AddressPrecision.APPROXIMATE);
        return summary;
    }

    private JobDetailDto detail() {
        AddressComponentsDto components = new AddressComponentsDto();
        components.setNeighborhood("Moda");
        components.setThoroughfare("Caferağa Sokak");
        components.setBuildingNumber("12");
        components.setFloor("3");
        components.setUnitNumber("4");
        components.setPostalCode("34710");
        components.setAdditionalDirections("Zil çalışmıyor");
        AddressPositionDto position = new AddressPositionDto();
        position.setLatitude(new BigDecimal("40.987654"));
        position.setLongitude(new BigDecimal("29.034567"));
        AddressDto address = new AddressDto();
        address.setCityName("İstanbul");
        address.setDistrictName("Kadıköy");
        address.setComponents(components);
        address.setPosition(position);
        address.setDisplayAddress(DISPLAY);
        JobDetailDto detail = new JobDetailDto();
        detail.setAddress(address);
        return detail;
    }
}
