package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.user.response.PublicProfileDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.File;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployerSurfaceRulesTest {

    private JobApplicationService jobApplicationService;
    private SecurityService securityService;
    private PublicProfileManager manager;

    private final User worker = worker();

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        jobApplicationService = mock(JobApplicationService.class);
        securityService = mock(SecurityService.class);

        StorageUrlResolver urls = mock(StorageUrlResolver.class);
        when(urls.urlFor(any())).thenAnswer(call -> "https://host/" + call.getArgument(0));

        manager = new PublicProfileManager(userService, jobApplicationService, securityService,
                new MediaUrlResolver(urls));

        when(userService.getById(worker.getId())).thenReturn(worker);
    }

    private static User worker() {
        City city = new City();
        city.setName("İstanbul");

        District district = new District();
        district.setName("Beşiktaş");

        Address address = new Address();
        address.setCity(city);
        address.setDistrict(district);
        address.setNeighborhood("Sinanpaşa Mahallesi");
        address.setThoroughfare("Barbaros Bulvarı");
        address.setBuildingNumber("12");
        address.setUnitNumber("4");

        File cv = new File();
        cv.setFileUrl("files/" + UUID.randomUUID() + ".pdf");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ayşe");
        user.setLastName("Yılmaz");
        user.setJobTitle("Barista");
        user.setAbout("Üç yıldır kahve hazırlıyorum.");
        user.setAddress(address);
        user.setCv(cv);
        return user;
    }

    @Test
    void theProfileNeverCarriesTheStreetAddress() {
        when(securityService.getAuthenticatedUserId()).thenReturn(UUID.randomUUID());

        PublicProfileDto profile = manager.getPublicProfile(worker.getId());

        assertEquals("İstanbul", profile.cityName());
        assertEquals("Beşiktaş", profile.districtName());
        assertEquals("Barista", profile.jobTitle());
        assertFalse(profile.toString().contains("Barbaros"));
    }

    @Test
    void aStrangerDoesNotSeeTheCvLink() {
        when(securityService.getAuthenticatedUserId()).thenReturn(UUID.randomUUID());
        when(jobApplicationService.hasApplicationForEmployer(any(), any())).thenReturn(false);

        assertNull(manager.getPublicProfile(worker.getId()).cvUrl());
    }

    @Test
    void theEmployerTheyAppliedToSeesTheCvLink() {
        UUID employerId = UUID.randomUUID();
        when(securityService.getAuthenticatedUserId()).thenReturn(employerId);
        when(jobApplicationService.hasApplicationForEmployer(employerId, worker.getId())).thenReturn(true);

        assertNotNull(manager.getPublicProfile(worker.getId()).cvUrl());
    }

    @Test
    void peopleAlwaysSeeTheirOwnCvLink() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertNotNull(manager.getPublicProfile(worker.getId()).cvUrl());
        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void aProfileWithoutACvSaysNothingAboutIt() {
        worker.setCv(null);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertNull(manager.getPublicProfile(worker.getId()).cvUrl());
    }
}
