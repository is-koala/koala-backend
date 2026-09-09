package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.JobAlertService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.JobAlertMessages;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.JobAlertProperties;
import com.exskylab.koala.dataAccess.JobAlertDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class JobAlertManager implements JobAlertService {

    private final JobAlertDao jobAlertDao;
    private final JobCategoryService jobCategoryService;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final JobAlertProperties properties;

    public JobAlertManager(JobAlertDao jobAlertDao, JobCategoryService jobCategoryService,
                           NotificationService notificationService, SecurityService securityService,
                           JobAlertProperties properties) {
        this.jobAlertDao = jobAlertDao;
        this.jobCategoryService = jobCategoryService;
        this.notificationService = notificationService;
        this.securityService = securityService;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobAlert> getMyAlerts() {
        return jobAlertDao.findAllByUserIdOrderByCreatedAtDesc(securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public JobAlert createAlert(SaveJobAlertDto request) {
        User currentUser = securityService.getAuthenticatedUser();

        if (jobAlertDao.findAllByUserIdOrderByCreatedAtDesc(currentUser.getId()).size()
                >= properties.getMaxPerUser()) {
            throw new ValidationException(JobAlertMessages.TOO_MANY);
        }

        JobAlert alert = new JobAlert();
        alert.setUser(currentUser);
        apply(alert, request);

        JobAlert saved = jobAlertDao.save(alert);
        log.info("Job alert {} created for user {}", saved.getId(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public JobAlert updateAlert(UUID alertId, SaveJobAlertDto request) {
        JobAlert alert = requireOwned(alertId);
        apply(alert, request);

        JobAlert saved = jobAlertDao.save(alert);
        log.info("Job alert {} updated", alertId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteAlert(UUID alertId) {
        jobAlertDao.delete(requireOwned(alertId));
        log.info("Job alert {} deleted", alertId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobAlert> matchingAlerts(Job job) {
        return jobAlertDao.findAllByActiveTrue().stream()
                .filter(alert -> matches(alert, job))
                .toList();
    }

    @Override
    @Transactional
    public void notifyMatching(Job job) {
        if (!properties.isEnabled()) {
            return;
        }

        UUID creatorId = job.getCreator() == null ? null : job.getCreator().getId();
        List<JobAlert> alerts = matchingAlerts(job).stream()
                .filter(alert -> !alert.getUser().getId().equals(creatorId))
                .toList();
        notificationService.preparePushRecipients(alerts.stream()
                .map(alert -> alert.getUser().getId())
                .toList());

        for (JobAlert alert : alerts) {
            try {
                SendPushToUserDto push = new SendPushToUserDto();
                push.setRecipient(alert.getUser());
                push.setTitle(alert.getAlertName());
                push.setBody(job.getTitle());
                push.setCategory(NotificationCategory.JOB_ALERTS);

                notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);

                alert.setLastNotifiedAt(Instant.now());
                jobAlertDao.save(alert);
            } catch (Exception e) {
                log.warn("Job alert {} could not be delivered: {}", alert.getId(), e.getMessage());
            }
        }
    }

    boolean matches(JobAlert alert, Job job) {
        if (job.getStatus() != JobStatus.OPEN) {
            return false;
        }
        if (alert.getCategory() != null && (job.getCategory() == null
                || !alert.getCategory().getId().equals(job.getCategory().getId()))) {
            return false;
        }
        if (alert.getType() != null && alert.getType() != job.getType()) {
            return false;
        }
        if (alert.getMinSalary() != null
                && (job.getSalary() == null || job.getSalary().compareTo(alert.getMinSalary()) < 0)) {
            return false;
        }
        if (alert.getCityName() != null && !matchesCity(alert.getCityName(), job)) {
            return false;
        }
        return alert.getDistrictName() == null || matchesDistrict(alert.getDistrictName(), job);
    }

    private boolean matchesCity(String cityName, Job job) {
        return job.getAddress() != null && job.getAddress().getCity() != null
                && cityName.equalsIgnoreCase(job.getAddress().getCity().getName());
    }

    private boolean matchesDistrict(String districtName, Job job) {
        return job.getAddress() != null && job.getAddress().getDistrict() != null
                && districtName.equalsIgnoreCase(job.getAddress().getDistrict().getName());
    }

    private JobAlert requireOwned(UUID alertId) {
        JobAlert alert = jobAlertDao.findWithDetailsById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAlertMessages.NOT_FOUND));

        if (!alert.getUser().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(JobAlertMessages.NOT_FOUND);
        }
        return alert;
    }

    private void apply(JobAlert alert, SaveJobAlertDto request) {
        alert.setAlertName(request.getAlertName());
        alert.setCityName(request.getCityName());
        alert.setDistrictName(request.getDistrictName());
        alert.setMinSalary(request.getMinSalary());
        alert.setType(request.getType());

        if (request.getActive() != null) {
            alert.setActive(request.getActive());
        }

        alert.setCategory(request.getCategoryId() == null ? null
                : jobCategoryService.getById(request.getCategoryId()));

        Objects.requireNonNull(alert.getAlertName());
    }
}
