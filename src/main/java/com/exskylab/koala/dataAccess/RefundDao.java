package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefundDao extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<Refund> findAllByPaymentIdInOrderByCreatedAtDesc(Collection<UUID> paymentIds);
}
