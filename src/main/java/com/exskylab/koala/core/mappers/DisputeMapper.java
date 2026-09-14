package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeEvidenceDto;
import com.exskylab.koala.core.dtos.dispute.response.StaffDisputeEvidenceDto;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeSide;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.entities.EvidenceUploaderSide;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DisputeMapper {

    @Named("participantView")
    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "openedBySide", expression = "java(openedBySide(dispute))")
    @Mapping(target = "resolvedByStaffId", ignore = true)
    DisputeDto toDisputeDto(Dispute dispute);

    @IterableMapping(qualifiedByName = "participantView")
    List<DisputeDto> toDisputeDtoList(List<Dispute> disputes);

    @Named("staffView")
    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "openedBySide", expression = "java(openedBySide(dispute))")
    @Mapping(target = "resolvedByStaffId", source = "resolvedByStaff.id")
    DisputeDto toStaffDisputeDto(Dispute dispute);

    @IterableMapping(qualifiedByName = "staffView")
    List<DisputeDto> toStaffDisputeDtoList(List<Dispute> disputes);

    default DisputeSide openedBySide(Dispute dispute) {
        if (dispute.getOpenedBy() == null || dispute.getJobAssignment() == null
                || dispute.getJobAssignment().getWorker() == null) {
            return null;
        }
        return dispute.getOpenedBy().getId().equals(dispute.getJobAssignment().getWorker().getId())
                ? DisputeSide.WORKER
                : DisputeSide.EMPLOYER;
    }

    @Mapping(target = "disputeId", source = "dispute.id")
    @Mapping(target = "fileName", source = "originalFileName")
    DisputeEvidenceDto toEvidenceDto(DisputeEvidence evidence);

    List<DisputeEvidenceDto> toEvidenceDtoList(List<DisputeEvidence> evidence);

    @Mapping(target = "disputeId", source = "evidence.dispute.id")
    @Mapping(target = "fileName", source = "evidence.originalFileName")
    @Mapping(target = "uploaderSide", source = "uploaderSide")
    StaffDisputeEvidenceDto toStaffEvidenceDto(DisputeEvidence evidence,
                                               EvidenceUploaderSide uploaderSide);
}
