package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileContractTest {

    private static final String PUBLIC_URL = "https://cdn.koala.test";

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties();
        storage.setPublicUrl(PUBLIC_URL);
        StorageUrlResolver urls0 = new StorageUrlResolver(storage,
                new AppProperties("http://localhost:8080", "http://localhost:3000"));
        userMapper = new UserMapperImpl(new MediaUrlResolver(urls0), new AddressMapperImpl(), new EducationMapperImpl(), new ExperienceMapperImpl());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void experiencesComeBackAsStructuredRecordsNotFreeText() {
        Experience experience = new Experience();
        experience.setId(UUID.randomUUID());
        experience.setTitle("Barista");
        experience.setCompanyName("Kahve Dünyası");
        experience.setStartDate(LocalDate.of(2021, 6, 1));

        User user = user();
        user.setExperiences(List.of(experience));

        var experiences = userMapper.toUserMeResponseDto(user, null, null, null).getExperiences();

        assertEquals(1, experiences.size());
        assertEquals("Barista", experiences.getFirst().title());
        assertEquals("Kahve Dünyası", experiences.getFirst().companyName());
        assertNull(experiences.getFirst().endDate(), "devam eden deneyimin bitişi olmaz");
    }

    @Test
    void theProfileCarriesAJobTitleOfItsOwn() {
        User user = user();
        user.setJobTitle("Fotoğrafçı");

        assertEquals("Fotoğrafçı", userMapper.toUserMeResponseDto(user, null, null, null).getJobTitle());
    }

    @Test
    void theIbanComesBackMaskedNotWhole() {
        var dto = userMapper.toUserMeResponseDto(user(), "TR33***4444", null, null);

        assertEquals("TR33***4444", dto.getMaskedIban());
    }

    @Test
    void aUserWithoutABankAccountHasNoIbanField() {
        assertNull(userMapper.toUserMeResponseDto(user(), null, null, null).getMaskedIban());
    }

    @Test
    void theCvIsNeverExposedAsAPermanentPublicUrl() {
        File cv = new File();
        cv.setFileUrl("files/abc-cv.pdf");

        User user = user();
        user.setCv(cv);

        String cvUrl = userMapper.toUserMeResponseDto(user, null, null, null).getCvUrl();

        assertEquals("http://localhost:8080/uploads/files/abc-cv.pdf", cvUrl);
        assertFalse(cvUrl.startsWith(PUBLIC_URL),
                "özgeçmiş CDN üzerinden herkese açık olmamalı, kimlik doğrulamalı uçtan geçmeli");
    }

    @Test
    void aProfilePictureStillGoesStraightToTheCdn() {
        Image picture = new Image();
        picture.setFileUrl("images/abc-foto.png");

        User user = user();
        user.setProfilePicture(picture);

        assertEquals(PUBLIC_URL + "/images/abc-foto.png",
                userMapper.toUserMeResponseDto(user, null, null, null).getProfilePictureUrl());
    }

    @Test
    void aUserWithoutACvHasNoUrl() {
        assertNull(userMapper.toUserMeResponseDto(user(), null, null, null).getCvUrl());
    }

    @Test
    void educationsAreReadableFromTheProfile() {
        University university = new University();
        university.setId(UUID.randomUUID());
        university.setName("Boğaziçi Üniversitesi");

        Faculty faculty = new Faculty();
        faculty.setId(UUID.randomUUID());
        faculty.setName("Mühendislik Fakültesi");
        faculty.setUniversity(university);

        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setName("Bilgisayar Mühendisliği");
        department.setFaculty(faculty);

        Education education = new Education();
        education.setId(UUID.randomUUID());
        education.setUniversity(university);
        education.setFaculty(faculty);
        education.setDepartment(department);
        education.setGpa(3.4);
        education.setStartDate(LocalDate.of(2020, 9, 1));

        User user = user();
        user.setEducations(List.of(education));

        var dto = userMapper.toUserMeResponseDto(user, null, null, null);

        assertEquals(1, dto.getEducations().size());
        assertEquals("Boğaziçi Üniversitesi", dto.getEducations().getFirst().universityName());
        assertEquals("Bilgisayar Mühendisliği", dto.getEducations().getFirst().departmentName());
    }
}
