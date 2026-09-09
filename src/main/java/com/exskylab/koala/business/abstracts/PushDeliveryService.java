package com.exskylab.koala.business.abstracts;

import java.util.UUID;

public interface PushDeliveryService {

    void deliver(UUID notificationId);

    int deliverDueBatch();

    int recoverStaleClaims();
}
