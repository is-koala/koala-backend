package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.entities.Experience;

import java.util.List;
import java.util.UUID;

public interface ExperienceService {

    List<Experience> getExperiencesOf(UUID userId);

    List<Experience> getMyExperiences();

    Experience addExperience(SaveExperienceDto request);

    Experience updateExperience(UUID experienceId, SaveExperienceDto request);

    void deleteExperience(UUID experienceId);
}
