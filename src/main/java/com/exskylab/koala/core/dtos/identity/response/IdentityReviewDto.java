package com.exskylab.koala.core.dtos.identity.response;

import com.exskylab.koala.entities.IdentityReviewStatus;
import com.exskylab.koala.entities.IdentityVerification;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
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
    private UUID frontDocumentId;
    private UUID backDocumentId;
    private Instant createdAt;

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
        if (review.isVerified()) {
            dto.setStatus(IdentityReviewStatus.APPROVED);
        } else if (review.getRejectedAt() != null) {
            dto.setStatus(IdentityReviewStatus.REJECTED);
        } else {
            dto.setStatus(IdentityReviewStatus.PENDING);
        }
        dto.setRejectionReason(review.getRejectionReason());
        dto.setRejectedAt(review.getRejectedAt());
        dto.setVerifiedAt(review.getVerifiedAt());
        dto.setReviewedByStaffId(review.getReviewedByStaffId());
        if (review.getVerificationDocument() != null) {
            dto.setFrontDocumentId(review.getVerificationDocument().getId());
        }
        if (review.getBackDocument() != null) {
            dto.setBackDocumentId(review.getBackDocument().getId());
        }
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
