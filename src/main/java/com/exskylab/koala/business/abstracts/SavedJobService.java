package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Job;

import java.util.List;
import java.util.UUID;

public interface SavedJobService {

    List<Job> getMySavedJobs();

    void saveJob(UUID jobId);

    void removeSavedJob(UUID jobId);
}
