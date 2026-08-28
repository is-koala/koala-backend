package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.SavedJobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.SavedJobMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SavedJobManager implements SavedJobService {

    private final UserService userService;
    private final JobService jobService;
    private final SecurityService securityService;

    public SavedJobManager(UserService userService, JobService jobService, SecurityService securityService) {
        this.userService = userService;
        this.jobService = jobService;
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
            log.debug("Job {} already saved by user {}", jobId, user.getId());
            return;
        }

        Job job = jobService.getById(jobId);

        saved.add(job);
        user.setSavedJobs(saved);
        userService.save(user);
        log.info("Job {} saved by user {}", jobId, user.getId());
    }

    @Override
    @Transactional
    public void removeSavedJob(UUID jobId) {
        User user = securityService.getAuthenticatedUser();
        List<Job> saved = savedJobsOf(user);

        if (saved.removeIf(job -> job.getId().equals(jobId))) {
            user.setSavedJobs(saved);
            userService.save(user);
            log.info("Job {} removed from saved list of user {}", jobId, user.getId());
        }
    }

    private List<Job> currentUserWithSavedJobs() {
        return savedJobsOf(securityService.getAuthenticatedUser());
    }

    private static List<Job> savedJobsOf(User user) {
        return user.getSavedJobs() == null ? new ArrayList<>() : new ArrayList<>(user.getSavedJobs());
    }
}
