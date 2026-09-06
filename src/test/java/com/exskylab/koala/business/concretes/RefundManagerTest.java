package com.exskylab.koala.business.concretes;

import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundManagerTest {

    @Test
    void aPendingRefundIsDurablyMarkedAsProcessing() {
        RefundDao refundDao = mock(RefundDao.class);
        UUID paymentId = UUID.randomUUID();
        Refund refund = new Refund();
        refund.setStatus(RefundStatus.PENDING);
        when(refundDao.findAllByPaymentIdOrderByCreatedAtDesc(paymentId)).thenReturn(List.of(refund));

        new RefundManager(refundDao).markProcessing(paymentId);

        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
        verify(refundDao).save(any(Refund.class));
    }
}
