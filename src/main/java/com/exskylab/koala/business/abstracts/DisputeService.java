package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.dtos.dispute.response.StaffDisputeEvidenceDto;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.entities.DisputeResolution;
import com.exskylab.koala.entities.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.exskylab.koala.dataAccess.PartyLink;

import com.exskylab.koala.core.helpers.CreatedAtRange;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DisputeService {

    Dispute openDispute(UUID jobAssignmentId, String reason);

    Dispute resolveDispute(StaffPrincipal actor, UUID disputeId, DisputeResolution resolution, String note);

    List<Dispute> getForAssignment(UUID jobAssignmentId);

    List<Dispute> findAllForAssignment(UUID jobAssignmentId);

    List<Dispute> findAllForAssignments(Collection<UUID> jobAssignmentIds);

    Page<Dispute> searchForStaff(DisputeStatus status, String query, UUID userId, CreatedAtRange createdAt,
                                 Pageable pageable);

    Dispute getForStaff(UUID disputeId);

    boolean hasActiveDispute(UUID jobAssignmentId);

    DisputeEvidence attachEvidence(UUID disputeId, MultipartFile file);

    List<DisputeEvidence> getEvidence(UUID disputeId);

    DisputeEvidence getEvidenceForDownload(UUID disputeId, UUID evidenceId);

    List<StaffDisputeEvidenceDto> getEvidenceForStaff(UUID disputeId);

    DisputeEvidence getEvidenceForStaffDownload(UUID disputeId, UUID evidenceId);

    long countInvolving(UUID userId);

    List<PartyLink> findActiveLinksForUsers(Collection<UUID> userIds);
}
