package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobTemplateService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.JobTemplateMessages;
import com.exskylab.koala.core.dtos.jobTemplate.request.CreateJobTemplateDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.dataAccess.JobTemplateDao;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobTemplate;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.business.abstracts.JobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class JobTemplateManager implements JobTemplateService {

    private final JobTemplateDao jobTemplateDao;
    private final JobService jobService;
    private final CompanyContactService companyContactService;
    private final SecurityService securityService;

    public JobTemplateManager(JobTemplateDao jobTemplateDao, JobService jobService,
                              CompanyContactService companyContactService, SecurityService securityService) {
        this.jobTemplateDao = jobTemplateDao;
        this.jobService = jobService;
        this.companyContactService = companyContactService;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobTemplate> getMyTemplates() {
        return jobTemplateDao.findAllByOwnerIdOrderByCreatedAtDesc(securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public JobTemplate createFromJob(CreateJobTemplateDto request) {
        Job job = jobService.getById(request.getJobId());

        User owner = securityService.getAuthenticatedUser();
        assertCanCopy(job, owner.getId());

        JobTemplate template = new JobTemplate();
        template.setOwner(owner);
        template.setCompany(job.getCompany());
        template.setTemplateName(request.getTemplateName());
        template.setCategory(job.getCategory());
        template.setTitle(job.getTitle());
        template.setDescription(job.getDescription());
        template.setSector(job.getSector());
        template.setPosition(job.getPosition());
        template.setDuties(job.getDuties());
        template.setDressCode(job.getDressCode());
        template.setType(job.getType());
        template.setSalaryType(job.getSalaryType());
        template.setSalary(job.getSalary());
        template.setMinAge(job.getMinAge());
        template.setMaxAge(job.getMaxAge());
        template.setExperienceRequired(job.isExperienceRequired());
        template.setInsuranceRequired(job.isInsuranceRequired());

        JobTemplate saved = jobTemplateDao.save(template);
        log.info("Job template {} created from job {}", saved.getId(), job.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public JobTemplate getMyTemplate(UUID templateId) {
        JobTemplate template = jobTemplateDao.findWithDetailsById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(JobTemplateMessages.NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!template.getOwner().getId().equals(actorId)) {
            throw Concealment.notFound(JobTemplateMessages.NOT_FOUND, actorId, templateId,
                    "job template belongs to another user");
        }
        return template;
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID templateId) {
        jobTemplateDao.delete(getMyTemplate(templateId));
        log.info("Job template {} deleted", templateId);
    }

    private void assertCanCopy(Job job, UUID userId) {
        boolean owns = job.getCreator() != null && job.getCreator().getId().equals(userId);
        boolean throughCompany = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), userId);

        if (!owns && !throughCompany) {
            throw new ResourceNotFoundException(JobTemplateMessages.JOB_NOT_FOUND);
        }
    }
}
