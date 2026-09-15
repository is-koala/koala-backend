package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.identity.response.AdminIdentityRevealDto;
import com.exskylab.koala.core.dtos.identity.response.WorkerIdentityDto;
import com.exskylab.koala.core.helpers.DisplayNames;
import com.exskylab.koala.entities.IdentityReveal;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface IdentityRevealMapper {

    @Mapping(target = "fullName", source = "worker", qualifiedByName = "fullName")
    @Mapping(target = "verifiedAt", source = "identityVerifiedAt")
    @Mapping(target = "verificationMethod", source = "identityVerificationMethod")
    WorkerIdentityDto toWorkerIdentityDto(User worker);

    @Mapping(target = "id", source = "reveal.id")
    @Mapping(target = "viewerId", source = "reveal.viewer.id")
    @Mapping(target = "viewerFullName", source = "viewer", qualifiedByName = "fullName")
    @Mapping(target = "jobAssignmentId", source = "reveal.jobAssignment.id")
    @Mapping(target = "jobId", source = "reveal.jobAssignment.job.id")
    @Mapping(target = "jobTitle", source = "reveal.jobAssignment.job.title")
    @Mapping(target = "revealedAt", source = "reveal.revealedAt")
    @Mapping(target = "ipAddress", source = "reveal.ipAddress")
    AdminIdentityRevealDto toAdminIdentityRevealDto(IdentityReveal reveal, User viewer);

    @Named("fullName")
    static String fullName(User user) {
        return user == null ? null : DisplayNames.of(user.getFirstName(), user.getLastName());
    }
}
