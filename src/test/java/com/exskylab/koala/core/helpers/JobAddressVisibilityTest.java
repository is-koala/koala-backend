package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.address.response.AddressPositionDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
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
    void aPublicCompanyJobRevealsTheExactAddressToEveryone() {
        job.setAddressPublic(true);
        job.setEmployerType(JobEmployerType.COMPANY);
        JobDetailDto detail = detail();

        visibility.protect(job, detail, null, false);

        assertEquals(AddressPrecision.EXACT, detail.getAddressPrecision());
        assertEquals("4", detail.getAddress().getComponents().getUnitNumber());
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

    private JobDetailDto detail() {
        AddressComponentsDto components = new AddressComponentsDto();
        components.setNeighborhood("Moda");
        components.setThoroughfare("Caferağa Sokak");
        components.setBuildingNumber("12");
        components.setUnitNumber("4");
        AddressPositionDto position = new AddressPositionDto();
        position.setLatitude(new BigDecimal("40.987654"));
        position.setLongitude(new BigDecimal("29.034567"));
        AddressDto address = new AddressDto();
        address.setCityName("İstanbul");
        address.setDistrictName("Kadıköy");
        address.setComponents(components);
        address.setPosition(position);
        address.setDisplayAddress("Caferağa Sokak No: 12 D: 4, Moda, Kadıköy, İstanbul");
        JobDetailDto detail = new JobDetailDto();
        detail.setAddress(address);
        return detail;
    }
}
