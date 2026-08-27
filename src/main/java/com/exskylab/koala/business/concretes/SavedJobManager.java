package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SavedJobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.SavedJobMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SavedJobManager implements SavedJobService {

    private final Logger logger = LoggerFactory.getLogger(SavedJobManager.class);

    private final UserDao userDao;
    private final JobDao jobDao;
    private final SecurityService securityService;

    public SavedJobManager(UserDao userDao, JobDao jobDao, SecurityService securityService) {
        this.userDao = userDao;
        this.jobDao = jobDao;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Job> getMySavedJobs() {
        return List.copyOf(currentUserWithSavedJobs());
    }

    @Override
    @Transactional
    public void saveJob(UUID jobId) {
        User user = securityService.getAuthenticatedUser();
        List<Job> saved = savedJobsOf(user);

        if (saved.stream().anyMatch(job -> job.getId().equals(jobId))) {
            logger.debug("Job {} already saved by user {}", jobId, user.getId());
            return;
        }

        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(SavedJobMessages.JOB_NOT_FOUND));

        saved.add(job);
        user.setSavedJobs(saved);
        userDao.save(user);
        logger.info("Job {} saved by user {}", jobId, user.getId());
    }

    @Override
    @Transactional
    public void removeSavedJob(UUID jobId) {
        User user = securityService.getAuthenticatedUser();
        List<Job> saved = savedJobsOf(user);

        if (saved.removeIf(job -> job.getId().equals(jobId))) {
            user.setSavedJobs(saved);
            userDao.save(user);
            logger.info("Job {} removed from saved list of user {}", jobId, user.getId());
        }
    }

    private List<Job> currentUserWithSavedJobs() {
        return savedJobsOf(securityService.getAuthenticatedUser());
    }

    private static List<Job> savedJobsOf(User user) {
        return user.getSavedJobs() == null ? new ArrayList<>() : new ArrayList<>(user.getSavedJobs());
    }
}
