package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.AcademicReferenceService;
import com.exskylab.koala.business.abstracts.EducationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.dataAccess.EducationDao;
import com.exskylab.koala.entities.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class EducationManager implements EducationService {

    private final EducationDao educationDao;
    private final AcademicReferenceService academicReferenceService;
    private final SecurityService securityService;

    public EducationManager(EducationDao educationDao,
                            AcademicReferenceService academicReferenceService,
                            SecurityService securityService) {
        this.educationDao = educationDao;
        this.academicReferenceService = academicReferenceService;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Education> getMyEducations() {
        return educationDao.findAllByUserIdOrderByStartDateDesc(securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public Education addEducation(SaveEducationDto request) {
        Education education = new Education();
        education.setUser(securityService.getAuthenticatedUser());
        apply(education, request);

        Education saved = educationDao.save(education);
        log.info("Education {} added for user {}", saved.getId(), education.getUser().getId());
        return saved;
    }

    @Override
    @Transactional
    public Education updateEducation(UUID educationId, SaveEducationDto request) {
        Education education = requireOwned(educationId);
        apply(education, request);

        Education saved = educationDao.save(education);
        log.info("Education {} updated", educationId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteEducation(UUID educationId) {
        educationDao.delete(requireOwned(educationId));
        log.info("Education {} deleted", educationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<University> getUniversities() {
        return academicReferenceService.getUniversities();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Faculty> getFaculties(UUID universityId) {
        return academicReferenceService.getFaculties(universityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getDepartments(UUID facultyId) {
        return academicReferenceService.getDepartments(facultyId);
    }

    private Education requireOwned(UUID educationId) {
        Education education = educationDao.findWithDetailsById(educationId)
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!education.getUser().getId().equals(actorId)) {
            throw Concealment.notFound(EducationMessages.NOT_FOUND, actorId, educationId,
                    "education belongs to another user");
        }
        return education;
    }

    private void apply(Education education, SaveEducationDto request) {
        University university = academicReferenceService.getUniversityById(request.getUniversityId());
        Faculty faculty = academicReferenceService.getFacultyById(request.getFacultyId());
        Department department = academicReferenceService.getDepartmentById(request.getDepartmentId());

        if (!faculty.getUniversity().getId().equals(university.getId())) {
            throw new ValidationException(EducationMessages.FACULTY_NOT_IN_UNIVERSITY);
        }
        if (!department.getFaculty().getId().equals(faculty.getId())) {
            throw new ValidationException(EducationMessages.DEPARTMENT_NOT_IN_FACULTY);
        }

        education.setUniversity(university);
        education.setFaculty(faculty);
        education.setDepartment(department);
        education.setType(request.getType());
        education.setGpa(request.getGpa());
        education.setStartDate(request.getStartDate());
        education.setEndDate(request.getEndDate());
    }
}
