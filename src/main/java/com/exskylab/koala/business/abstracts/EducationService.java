package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.entities.Department;
import com.exskylab.koala.entities.Education;
import com.exskylab.koala.entities.Faculty;
import com.exskylab.koala.entities.University;

import java.util.List;
import java.util.UUID;

public interface EducationService {

    List<Education> getMyEducations();

    Education addEducation(SaveEducationDto request);

    Education updateEducation(UUID educationId, SaveEducationDto request);

    void deleteEducation(UUID educationId);

    List<University> getUniversities();

    List<Faculty> getFaculties(UUID universityId);

    List<Department> getDepartments(UUID facultyId);
}
