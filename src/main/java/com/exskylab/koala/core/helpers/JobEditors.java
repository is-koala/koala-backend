package com.exskylab.koala.core.helpers;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.entities.Job;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JobEditors {

    private final CompanyContactService companyContactService;

    public JobEditors(CompanyContactService companyContactService) {
        this.companyContactService = companyContactService;
    }

    public boolean isEditor(Job job, UUID userId) {
        if (userId == null) {
            return false;
        }
        boolean owner = job.getCreator() != null && userId.equals(job.getCreator().getId());
        boolean contact = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), userId);
        return owner || contact;
    }
}
