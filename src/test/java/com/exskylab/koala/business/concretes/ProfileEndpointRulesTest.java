package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.business.abstracts.AcademicReferenceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileEndpointRulesTest {

    private EducationDao educationDao;
    private UniversityDao universityDao;
    private FacultyDao facultyDao;
    private DepartmentDao departmentDao;
    private UserService userService;
    private JobService jobService;
    private SecurityService securityService;
    private AcademicReferenceService academicReferenceService;

    private EducationManager educationManager;
    private SavedJobManager savedJobManager;

    private final User me = user();
    private final User someoneElse = user();

    private University university;
    private Faculty faculty;
    private Department department;

    @BeforeEach
    void setUp() {
        educationDao = mock(EducationDao.class);
        universityDao = mock(UniversityDao.class);
        facultyDao = mock(FacultyDao.class);
        departmentDao = mock(DepartmentDao.class);
        userService = mock(UserService.class);
        jobService = mock(JobService.class);
        securityService = mock(SecurityService.class);

        academicReferenceService = mock(AcademicReferenceService.class);
        educationManager = new EducationManager(educationDao, academicReferenceService, securityService);
        savedJobManager = new SavedJobManager(userService, jobService, securityService);

        when(educationDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityService.getAuthenticatedUserId()).thenReturn(me.getId());
        when(securityService.getAuthenticatedUser()).thenReturn(me);

        university = new University();
        university.setId(UUID.randomUUID());

        faculty = new Faculty();
        faculty.setId(UUID.randomUUID());
        faculty.setUniversity(university);

        department = new Department();
        department.setId(UUID.randomUUID());
        department.setFaculty(faculty);

        when(academicReferenceService.getUniversityById(university.getId())).thenReturn(university);
        when(academicReferenceService.getFacultyById(faculty.getId())).thenReturn(faculty);
        when(academicReferenceService.getDepartmentById(department.getId())).thenReturn(department);
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setSavedJobs(new ArrayList<>());
        return u;
    }

    private SaveEducationDto request() {
        SaveEducationDto dto = new SaveEducationDto();
        dto.setUniversityId(university.getId());
        dto.setFacultyId(faculty.getId());
        dto.setDepartmentId(department.getId());
        dto.setType(GradeType.values()[0]);
        dto.setGpa(3.2);
        dto.setStartDate(LocalDate.of(2020, 9, 1));
        return dto;
    }

    @Test
    void anEducationIsSavedAgainstTheSignedInUser() {
        Education saved = educationManager.addEducation(request());

        assertEquals(me.getId(), saved.getUser().getId());
        assertEquals(department.getId(), saved.getDepartment().getId());
    }

    @Test
    void aFacultyFromAnotherUniversityIsRejected() {
        University other = new University();
        other.setId(UUID.randomUUID());
        when(academicReferenceService.getUniversityById(other.getId())).thenReturn(other);

        SaveEducationDto dto = request();
        dto.setUniversityId(other.getId());

        assertThrows(ValidationException.class, () -> educationManager.addEducation(dto));
        verify(educationDao, never()).save(any());
    }

    @Test
    void aDepartmentFromAnotherFacultyIsRejected() {
        Faculty other = new Faculty();
        other.setId(UUID.randomUUID());
        other.setUniversity(university);
        when(academicReferenceService.getFacultyById(other.getId())).thenReturn(other);

        SaveEducationDto dto = request();
        dto.setFacultyId(other.getId());

        assertThrows(ValidationException.class, () -> educationManager.addEducation(dto));
    }

    @Test
    void nobodyCanEditSomeoneElsesEducation() {
        Education theirs = new Education();
        theirs.setId(UUID.randomUUID());
        theirs.setUser(someoneElse);
        when(educationDao.findWithDetailsById(theirs.getId())).thenReturn(Optional.of(theirs));

        assertThrows(ResourceNotFoundException.class,
                () -> educationManager.updateEducation(theirs.getId(), request()));
        assertThrows(ResourceNotFoundException.class,
                () -> educationManager.deleteEducation(theirs.getId()));
        verify(educationDao, never()).delete(any());
    }

    @Test
    void savingAJobTwiceKeepsOneEntry() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        when(jobService.getById(job.getId())).thenReturn(job);

        savedJobManager.saveJob(job.getId());
        savedJobManager.saveJob(job.getId());

        assertEquals(1, me.getSavedJobs().size());
        verify(jobService, times(1)).getById(job.getId());
    }

    @Test
    void anUnknownJobCannotBeSaved() {
        UUID ghost = UUID.randomUUID();
        when(jobService.getById(ghost)).thenThrow(new ResourceNotFoundException("yok"));

        assertThrows(ResourceNotFoundException.class, () -> savedJobManager.saveJob(ghost));
    }

    @Test
    void removingAJobLeavesTheOthersAlone() {
        Job first = new Job();
        first.setId(UUID.randomUUID());
        Job second = new Job();
        second.setId(UUID.randomUUID());
        me.setSavedJobs(new ArrayList<>(List.of(first, second)));

        savedJobManager.removeSavedJob(first.getId());

        assertEquals(List.of(second.getId()), me.getSavedJobs().stream().map(Job::getId).toList());
    }

    @Test
    void removingAJobThatWasNeverSavedIsHarmless() {
        assertDoesNotThrow(() -> savedJobManager.removeSavedJob(UUID.randomUUID()));
        verify(userService, never()).save(any());
    }
}
