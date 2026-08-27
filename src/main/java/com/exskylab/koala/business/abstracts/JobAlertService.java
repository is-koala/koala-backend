package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAlert;

import java.util.List;
import java.util.UUID;

public interface JobAlertService {

    List<JobAlert> getMyAlerts();

    JobAlert createAlert(SaveJobAlertDto request);

    JobAlert updateAlert(UUID alertId, SaveJobAlertDto request);

    void deleteAlert(UUID alertId);

    List<JobAlert> matchingAlerts(Job job);

    void notifyMatching(Job job);
}
