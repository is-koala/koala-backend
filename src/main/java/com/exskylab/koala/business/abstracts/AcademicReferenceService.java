package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Department;
import com.exskylab.koala.entities.Faculty;
import com.exskylab.koala.entities.University;

import java.util.List;
import java.util.UUID;

public interface AcademicReferenceService {

    List<University> getUniversities();

    List<Faculty> getFaculties(UUID universityId);

    List<Department> getDepartments(UUID facultyId);

    University getUniversityById(UUID universityId);

    Faculty getFacultyById(UUID facultyId);

    Department getDepartmentById(UUID departmentId);
}
