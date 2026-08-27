package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.education.response.EducationDto;
import com.exskylab.koala.core.dtos.reference.response.DepartmentDto;
import com.exskylab.koala.core.dtos.reference.response.FacultyDto;
import com.exskylab.koala.core.dtos.reference.response.UniversityDto;
import com.exskylab.koala.entities.Department;
import com.exskylab.koala.entities.Education;
import com.exskylab.koala.entities.Faculty;
import com.exskylab.koala.entities.University;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface EducationMapper {

    @Mapping(target = "universityId", source = "university.id")
    @Mapping(target = "universityName", source = "university.name")
    @Mapping(target = "facultyId", source = "faculty.id")
    @Mapping(target = "facultyName", source = "faculty.name")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    EducationDto toEducationDto(Education education);

    List<EducationDto> toEducationDtoList(List<Education> educations);

    UniversityDto toUniversityDto(University university);

    List<UniversityDto> toUniversityDtoList(List<University> universities);

    FacultyDto toFacultyDto(Faculty faculty);

    List<FacultyDto> toFacultyDtoList(List<Faculty> faculties);

    DepartmentDto toDepartmentDto(Department department);

    List<DepartmentDto> toDepartmentDtoList(List<Department> departments);
}
