package com.exskylab.koala.core.dtos.identity.response;

import com.exskylab.koala.entities.IdentityReviewStatus;
import com.exskylab.koala.entities.IdentityVerification;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class IdentityReviewDto {

    private UUID id;
    private UUID userId;
    private String firstName;
    private String lastName;
    private String tcIdentityNumber;
    private String tcDocumentNumber;
    private LocalDate birthDate;
    private IdentityReviewStatus status;
    private String rejectionReason;
    private Instant rejectedAt;
    private Instant verifiedAt;
    private UUID reviewedByStaffId;
    private String reviewedByStaffName;
    private UUID selfieDocumentId;
    private UUID frontDocumentId;
    private UUID backDocumentId;
    private UUID togetherDocumentId;
    private Instant createdAt;

    public static IdentityReviewStatus statusOf(IdentityVerification review) {
        if (review.isVerified()) {
            return IdentityReviewStatus.APPROVED;
        }
        return review.getRejectedAt() != null ? IdentityReviewStatus.REJECTED : IdentityReviewStatus.PENDING;
    }

    public static IdentityReviewDto from(IdentityVerification review, Map<UUID, String> staffNames) {
        IdentityReviewDto dto = from(review);
        if (review.getReviewedByStaffId() != null) {
            dto.setReviewedByStaffName(staffNames.get(review.getReviewedByStaffId()));
        }
        return dto;
    }

    public static IdentityReviewDto from(IdentityVerification review) {
        IdentityReviewDto dto = new IdentityReviewDto();
        dto.setId(review.getId());
        if (review.getUser() != null) {
            dto.setUserId(review.getUser().getId());
        }
        dto.setFirstName(review.getFirstName());
        dto.setLastName(review.getLastName());
        dto.setTcIdentityNumber(review.getTcIdentityNumber());
        dto.setTcDocumentNumber(review.getTcDocumentNumber());
        dto.setBirthDate(review.getBirthDate());
        dto.setStatus(statusOf(review));
        dto.setRejectionReason(review.getRejectionReason());
        dto.setRejectedAt(review.getRejectedAt());
        dto.setVerifiedAt(review.getVerifiedAt());
        dto.setReviewedByStaffId(review.getReviewedByStaffId());
        if (review.getSelfieDocument() != null) {
            dto.setSelfieDocumentId(review.getSelfieDocument().getId());
        }
        if (review.getVerificationDocument() != null) {
            dto.setFrontDocumentId(review.getVerificationDocument().getId());
        }
        if (review.getBackDocument() != null) {
            dto.setBackDocumentId(review.getBackDocument().getId());
        }
        if (review.getTogetherDocument() != null) {
            dto.setTogetherDocumentId(review.getTogetherDocument().getId());
        }
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
