package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeEvidenceDto;
import com.exskylab.koala.core.dtos.dispute.response.StaffDisputeEvidenceDto;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.entities.EvidenceUploaderSide;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DisputeMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    DisputeDto toDisputeDto(Dispute dispute);

    List<DisputeDto> toDisputeDtoList(List<Dispute> disputes);

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
