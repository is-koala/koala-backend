package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.IdentityReviewStatus;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.VerificationDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IdentityDocumentReviewService {

    IdentityVerification submit(VerifyIdentityDto identity,
                                MultipartFile selfie,
                                MultipartFile front,
                                MultipartFile back,
                                MultipartFile together);

    IdentityVerification approve(StaffPrincipal actor, UUID reviewId);

    IdentityVerification reject(StaffPrincipal actor, UUID reviewId, String reason);

    Page<IdentityVerification> search(IdentityReviewStatus status, Pageable pageable);

    IdentityVerification getById(UUID reviewId);

    IdentityVerification latestForCurrentUser();

    VerificationDocument getDocumentForStaffDownload(UUID reviewId, UUID documentId);
}
