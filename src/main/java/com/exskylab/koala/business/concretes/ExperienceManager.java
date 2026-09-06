package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.ExperienceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ExperienceMessages;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.dataAccess.ExperienceDao;
import com.exskylab.koala.entities.Experience;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ExperienceManager implements ExperienceService {

    private final ExperienceDao experienceDao;
    private final SecurityService securityService;

    public ExperienceManager(ExperienceDao experienceDao, SecurityService securityService) {
        this.experienceDao = experienceDao;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Experience> getExperiencesOf(UUID userId) {
        return experienceDao.findAllByUserIdOrderByStartDateDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Experience> getMyExperiences() {
        return getExperiencesOf(securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public Experience addExperience(SaveExperienceDto request) {
        Experience experience = new Experience();
        experience.setUser(securityService.getAuthenticatedUser());
        apply(experience, request);

        Experience saved = experienceDao.save(experience);
        log.info("Experience {} added for user {}", saved.getId(), experience.getUser().getId());
        return saved;
    }

    @Override
    @Transactional
    public Experience updateExperience(UUID experienceId, SaveExperienceDto request) {
        Experience experience = requireOwned(experienceId);
        apply(experience, request);

        Experience saved = experienceDao.save(experience);
        log.info("Experience {} updated", experienceId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteExperience(UUID experienceId) {
        experienceDao.delete(requireOwned(experienceId));
        log.info("Experience {} deleted", experienceId);
    }

    private Experience requireOwned(UUID experienceId) {
        Experience experience = experienceDao.findWithUserById(experienceId)
                .orElseThrow(() -> new ResourceNotFoundException(ExperienceMessages.NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!experience.getUser().getId().equals(actorId)) {
            throw Concealment.notFound(ExperienceMessages.NOT_FOUND, actorId, experienceId,
                    "experience belongs to another user");
        }
        return experience;
    }

    private void apply(Experience experience, SaveExperienceDto request) {
        if (request.getStartDate().isAfter(LocalDate.now())) {
            throw new ValidationException(ExperienceMessages.START_IN_FUTURE);
        }
        experience.setTitle(request.getTitle());
        experience.setCompanyName(request.getCompanyName());
        experience.setEmploymentType(request.getEmploymentType());
        experience.setCityName(request.getCityName());
        experience.setStartDate(request.getStartDate());
        experience.setEndDate(request.getEndDate());
        experience.setDescription(request.getDescription());
    }
}
