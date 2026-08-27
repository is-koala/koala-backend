package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ExperienceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ExperienceMessages;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.ExperienceDao;
import com.exskylab.koala.entities.Experience;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExperienceManager implements ExperienceService {

    private final Logger logger = LoggerFactory.getLogger(ExperienceManager.class);

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
        logger.info("Experience {} added for user {}", saved.getId(), experience.getUser().getId());
        return saved;
    }

    @Override
    @Transactional
    public Experience updateExperience(UUID experienceId, SaveExperienceDto request) {
        Experience experience = requireOwned(experienceId);
        apply(experience, request);

        Experience saved = experienceDao.save(experience);
        logger.info("Experience {} updated", experienceId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteExperience(UUID experienceId) {
        experienceDao.delete(requireOwned(experienceId));
        logger.info("Experience {} deleted", experienceId);
    }

    private Experience requireOwned(UUID experienceId) {
        Experience experience = experienceDao.findWithUserById(experienceId)
                .orElseThrow(() -> new ResourceNotFoundException(ExperienceMessages.NOT_FOUND));

        if (!experience.getUser().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(ExperienceMessages.NOT_OWNER);
        }
        return experience;
    }

    private void apply(Experience experience, SaveExperienceDto request) {
        if (request.getStartDate().isAfter(LocalDate.now())) {
            throw new ValidationException(ExperienceMessages.START_IN_FUTURE);
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ValidationException(ExperienceMessages.END_BEFORE_START);
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
