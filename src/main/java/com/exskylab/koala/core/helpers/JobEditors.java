package com.exskylab.koala.core.helpers;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public List<User> editorsOf(Job job) {
        Map<UUID, User> editors = new LinkedHashMap<>();
        if (job.getCreator() != null) {
            editors.put(job.getCreator().getId(), job.getCreator());
        }
        if (job.getCompany() != null) {
            companyContactService.getContactsOfCompany(job.getCompany().getId())
                    .forEach(contact -> editors.putIfAbsent(contact.getUser().getId(), contact.getUser()));
        }
        return List.copyOf(editors.values());
    }
}
