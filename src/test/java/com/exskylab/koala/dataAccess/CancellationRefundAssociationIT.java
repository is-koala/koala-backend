package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.RefundStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CancellationRefundAssociationIT extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final UUID cancellationId = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CancellationDao cancellationDao;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from cancellations where id = ?", cancellationId);
        jdbcTemplate.update("delete from refunds where id = ?", refundId);
        jdbcTemplate.update("delete from payments where id = ?", paymentId);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
    }

    @Test
    void repositoryLoadsTheRefundPersistedForTheCancellation() {
        insertGraph(refundId);

        Cancellation cancellation = cancellationDao.findByJobAssignmentId(assignmentId).orElseThrow();

        assertEquals(refundId, cancellation.getRefund().getId());
        assertEquals(RefundStatus.PENDING, cancellation.getRefund().getStatus());
    }

    @Test
    void aLegacyCancellationDoesNotBorrowAnUnrelatedRefundFromItsAssignment() {
        insertGraph(null);

        Cancellation cancellation = cancellationDao.findByJobAssignmentId(assignmentId).orElseThrow();

        assertNull(cancellation.getRefund());
    }

    private void insertGraph(UUID cancellationRefundId) {
        jdbcTemplate.update("insert into jobs (id, created_at, is_deleted) values (?, now(), false)", jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status)
                values (?, now(), false, ?, ?, 'CANCELLED', 'REFUNDED')
                """, assignmentId, jobId, SYSTEM_USER_ID);
        jdbcTemplate.update("""
                insert into payments
                    (id, created_at, is_deleted, job_assignment_id, conversation_id, status, currency)
                values (?, now(), false, ?, ?, 'SUCCEEDED', 'TRY')
                """, paymentId, assignmentId, "cancellation-refund-" + paymentId);
        jdbcTemplate.update("""
                insert into refunds
                    (id, created_at, is_deleted, payment_id, amount, currency, status, reason)
                values (?, now(), false, ?, 1050.11, 'TRY', 'PENDING', 'başka iade')
                """, refundId, paymentId);
        jdbcTemplate.update("""
                insert into cancellations
                    (id, created_at, is_deleted, job_assignment_id, cancelled_by_id, cancelled_by_party,
                     reason, notice_minutes, fee_rate, fee_amount, refund_amount, refund_id, cancelled_at)
                values (?, now(), false, ?, ?, 'EMPLOYER', 'iptal', 120, 0.0750, 75.00, 1050.11, ?, now())
                """, cancellationId, assignmentId, SYSTEM_USER_ID, cancellationRefundId);
    }
}
