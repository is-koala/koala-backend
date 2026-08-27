package com.exskylab.koala.core.dtos.education.response;

import com.exskylab.koala.entities.GradeType;

import java.time.LocalDate;
import java.util.UUID;

public record EducationDto(UUID id, UUID universityId, String universityName,
                           UUID facultyId, String facultyName,
                           UUID departmentId, String departmentName,
                           GradeType type, double gpa, LocalDate startDate, LocalDate endDate) {
}
