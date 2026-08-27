package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobTemplate.request.CreateJobTemplateDto;
import com.exskylab.koala.entities.JobTemplate;

import java.util.List;
import java.util.UUID;

public interface JobTemplateService {

    List<JobTemplate> getMyTemplates();

    JobTemplate createFromJob(CreateJobTemplateDto request);

    JobTemplate getMyTemplate(UUID templateId);

    void deleteTemplate(UUID templateId);
}
