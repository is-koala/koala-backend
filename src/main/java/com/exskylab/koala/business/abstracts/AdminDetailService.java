package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserDetailDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminDetailService {

    AdminAssignmentDetailDto getAssignmentDetail(UUID assignmentId);

    List<AdminPaymentDto> getPaymentsForAssignment(UUID assignmentId);

    AdminPaymentDto getPayment(UUID paymentId);

    AdminUserDetailDto getUserDetail(StaffPrincipal actor, UUID userId);

    Page<CompanyReviewDto> searchCompanies(CompanyApprovalStatus status, String query, Pageable pageable);

    CompanyReviewDto getCompany(UUID companyId);
}
