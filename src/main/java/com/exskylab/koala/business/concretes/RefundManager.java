package com.exskylab.koala.business.concretes;

import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RefundManager {

    private final RefundDao refundDao;

    public RefundManager(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID paymentId) {
        refundDao.findAllByPaymentIdOrderByCreatedAtDesc(paymentId).stream().findFirst()
                .filter(refund -> refund.getStatus() == RefundStatus.PENDING)
                .ifPresent(refund -> {
                    refund.setStatus(RefundStatus.PROCESSING);
                    refundDao.save(refund);
                });
    }
}
