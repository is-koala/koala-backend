package com.exskylab.koala.core.helpers;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AssignmentParticipants {

    private final CompanyContactService companyContactService;

    public AssignmentParticipants(CompanyContactService companyContactService) {
        this.companyContactService = companyContactService;
    }

    public Optional<CancellationParty> partyOf(JobAssignment assignment, UUID userId) {
        if (assignment.getWorker() != null && assignment.getWorker().getId().equals(userId)) {
            return Optional.of(CancellationParty.WORKER);
        }

        Job job = assignment.getJob();
        if (job == null) {
            return Optional.empty();
        }

        if (job.getCreator() != null && job.getCreator().getId().equals(userId)) {
            return Optional.of(CancellationParty.EMPLOYER);
        }

        if (job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), userId)) {
            return Optional.of(CancellationParty.EMPLOYER);
        }

        return Optional.empty();
    }

    public boolean isParticipant(JobAssignment assignment, UUID userId) {
        return partyOf(assignment, userId).isPresent();
    }
}
