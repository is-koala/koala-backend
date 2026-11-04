package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.bankAccount.response.AdminBankAccountDto;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserDetailDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.IdentityVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminDetailService {

    AdminAssignmentDetailDto getAssignmentDetail(StaffPrincipal actor, UUID assignmentId);

    List<AdminPaymentDto> getPaymentsForAssignment(UUID assignmentId);

    AdminPaymentDto getPayment(UUID paymentId);

    AdminUserDetailDto getUserDetail(StaffPrincipal actor, UUID userId);

    List<AdminBankAccountDto> getBankAccountsOfUser(UUID userId);

    Page<AdminPaymentDto> searchPayments(List<PaymentAttemptStatus> statuses, Pageable pageable);

    Page<CompanyReviewDto> searchCompanies(CompanyApprovalStatus status, String query, Pageable pageable);

    CompanyReviewDto getCompany(UUID companyId);

    List<IdentityReviewDto> identityReviews(List<IdentityVerification> reviews);

    IdentityReviewDto identityReview(IdentityVerification review);

    List<AccountDeletionDto> deletionRequests(List<AccountDeletionRequest> requests);

    AccountDeletionDto deletionRequest(AccountDeletionRequest request);
}
