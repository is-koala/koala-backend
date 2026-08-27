package com.exskylab.koala.core.dtos.experience.response;

import com.exskylab.koala.entities.EmploymentType;

import java.time.LocalDate;
import java.util.UUID;

public record ExperienceDto(UUID id, String title, String companyName, EmploymentType employmentType,
                            String cityName, LocalDate startDate, LocalDate endDate, String description) {
}
