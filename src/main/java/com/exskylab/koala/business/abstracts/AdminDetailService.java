package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;

import java.util.List;
import java.util.UUID;

public interface AdminDetailService {

    AdminAssignmentDetailDto getAssignmentDetail(UUID assignmentId);

    List<AdminPaymentDto> getPaymentsForAssignment(UUID assignmentId);

    AdminPaymentDto getPayment(UUID paymentId);
}
