package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.EducationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.DepartmentDao;
import com.exskylab.koala.dataAccess.EducationDao;
import com.exskylab.koala.dataAccess.FacultyDao;
import com.exskylab.koala.dataAccess.UniversityDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EducationManager implements EducationService {

    private final Logger logger = LoggerFactory.getLogger(EducationManager.class);

    private final EducationDao educationDao;
    private final UniversityDao universityDao;
    private final FacultyDao facultyDao;
    private final DepartmentDao departmentDao;
    private final SecurityService securityService;

    public EducationManager(EducationDao educationDao, UniversityDao universityDao,
                            FacultyDao facultyDao, DepartmentDao departmentDao,
                            SecurityService securityService) {
        this.educationDao = educationDao;
        this.universityDao = universityDao;
        this.facultyDao = facultyDao;
        this.departmentDao = departmentDao;
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
        logger.info("Education {} added for user {}", saved.getId(), education.getUser().getId());
        return saved;
    }

    @Override
    @Transactional
    public Education updateEducation(UUID educationId, SaveEducationDto request) {
        Education education = requireOwned(educationId);
        apply(education, request);

        Education saved = educationDao.save(education);
        logger.info("Education {} updated", educationId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteEducation(UUID educationId) {
        educationDao.delete(requireOwned(educationId));
        logger.info("Education {} deleted", educationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<University> getUniversities() {
        return universityDao.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Faculty> getFaculties(UUID universityId) {
        return facultyDao.findAllByUniversityIdOrderByNameAsc(universityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getDepartments(UUID facultyId) {
        return departmentDao.findAllByFacultyIdOrderByNameAsc(facultyId);
    }

    private Education requireOwned(UUID educationId) {
        Education education = educationDao.findWithDetailsById(educationId)
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.NOT_FOUND));

        if (!education.getUser().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(EducationMessages.NOT_OWNER);
        }
        return education;
    }

    private void apply(Education education, SaveEducationDto request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(EducationMessages.END_BEFORE_START);
        }

        University university = universityDao.findById(request.getUniversityId())
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.UNIVERSITY_NOT_FOUND));
        Faculty faculty = facultyDao.findById(request.getFacultyId())
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.FACULTY_NOT_FOUND));
        Department department = departmentDao.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.DEPARTMENT_NOT_FOUND));

        if (!faculty.getUniversity().getId().equals(university.getId())) {
            throw new IllegalArgumentException(EducationMessages.FACULTY_NOT_IN_UNIVERSITY);
        }
        if (!department.getFaculty().getId().equals(faculty.getId())) {
            throw new IllegalArgumentException(EducationMessages.DEPARTMENT_NOT_IN_FACULTY);
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
