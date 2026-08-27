package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.SettlementOperation;
import com.exskylab.koala.entities.SettlementTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementService {

    Optional<SettlementTask> enqueue(Payment payment, JobAssignment assignment, SettlementOperation operation);

    boolean runTask(UUID taskId);

    List<UUID> findDueTaskIds();
}
