package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.identity.response.AdminIdentityRevealDto;
import com.exskylab.koala.core.dtos.identity.response.WorkerIdentityDto;
import com.exskylab.koala.entities.IdentityReveal;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface IdentityRevealMapper {

    @Mapping(target = "fullName", source = "worker", qualifiedByName = "fullName")
    @Mapping(target = "verifiedAt", source = "identityVerifiedAt")
    @Mapping(target = "verificationMethod", source = "identityVerificationMethod")
    WorkerIdentityDto toWorkerIdentityDto(User worker);

    @Mapping(target = "viewerId", source = "viewer.id")
    @Mapping(target = "viewerFullName", source = "viewer", qualifiedByName = "fullName")
    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "jobId", source = "jobAssignment.job.id")
    @Mapping(target = "jobTitle", source = "jobAssignment.job.title")
    AdminIdentityRevealDto toAdminIdentityRevealDto(IdentityReveal reveal);

    List<AdminIdentityRevealDto> toAdminIdentityRevealDtoList(List<IdentityReveal> reveals);

    @Named("fullName")
    static String fullName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }
}
