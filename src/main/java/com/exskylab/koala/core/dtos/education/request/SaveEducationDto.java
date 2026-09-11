package com.exskylab.koala.core.dtos.education.request;

import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.entities.GradeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveEducationDto {

    @NotNull(message = EducationMessages.UNIVERSITY_REQUIRED)
    private UUID universityId;

    @NotNull(message = EducationMessages.FACULTY_REQUIRED)
    private UUID facultyId;

    @NotNull(message = EducationMessages.DEPARTMENT_REQUIRED)
    private UUID departmentId;

    @NotNull(message = EducationMessages.TYPE_REQUIRED)
    private GradeType type;

    @NotNull(message = EducationMessages.GPA_REQUIRED)
    @DecimalMin(value = "0.0", message = EducationMessages.GPA_RANGE)
    @DecimalMax(value = "4.0", message = EducationMessages.GPA_RANGE)
    private Double gpa;

    @NotNull(message = EducationMessages.START_DATE_REQUIRED)
    private LocalDate startDate;

    private LocalDate endDate;

    @AssertTrue(message = EducationMessages.END_BEFORE_START)
    public boolean isEndAfterStart() {
        return endDate == null || startDate == null || !endDate.isBefore(startDate);
    }
}
