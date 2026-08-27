package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobTemplateService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.JobTemplateMessages;
import com.exskylab.koala.core.dtos.jobTemplate.request.CreateJobTemplateDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.JobTemplateDao;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobTemplate;
import com.exskylab.koala.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobTemplateManager implements JobTemplateService {

    private final Logger logger = LoggerFactory.getLogger(JobTemplateManager.class);

    private final JobTemplateDao jobTemplateDao;
    private final JobDao jobDao;
    private final CompanyContactService companyContactService;
    private final SecurityService securityService;

    public JobTemplateManager(JobTemplateDao jobTemplateDao, JobDao jobDao,
                              CompanyContactService companyContactService, SecurityService securityService) {
        this.jobTemplateDao = jobTemplateDao;
        this.jobDao = jobDao;
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
        Job job = jobDao.findById(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException(JobTemplateMessages.JOB_NOT_FOUND));

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
        logger.info("Job template {} created from job {}", saved.getId(), job.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public JobTemplate getMyTemplate(UUID templateId) {
        JobTemplate template = jobTemplateDao.findWithDetailsById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(JobTemplateMessages.NOT_FOUND));

        if (!template.getOwner().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(JobTemplateMessages.NOT_OWNER);
        }
        return template;
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID templateId) {
        jobTemplateDao.delete(getMyTemplate(templateId));
        logger.info("Job template {} deleted", templateId);
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
