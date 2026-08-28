package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AcademicReferenceService;
import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.DepartmentDao;
import com.exskylab.koala.dataAccess.FacultyDao;
import com.exskylab.koala.dataAccess.UniversityDao;
import com.exskylab.koala.entities.Department;
import com.exskylab.koala.entities.Faculty;
import com.exskylab.koala.entities.University;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AcademicReferenceManager implements AcademicReferenceService {

    private final UniversityDao universityDao;
    private final FacultyDao facultyDao;
    private final DepartmentDao departmentDao;

    public AcademicReferenceManager(UniversityDao universityDao, FacultyDao facultyDao,
                                    DepartmentDao departmentDao) {
        this.universityDao = universityDao;
        this.facultyDao = facultyDao;
        this.departmentDao = departmentDao;
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

    @Override
    @Transactional(readOnly = true)
    public University getUniversityById(UUID universityId) {
        return universityDao.findById(universityId)
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.UNIVERSITY_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Faculty getFacultyById(UUID facultyId) {
        return facultyDao.findById(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.FACULTY_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Department getDepartmentById(UUID departmentId) {
        return departmentDao.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(EducationMessages.DEPARTMENT_NOT_FOUND));
    }
}
