package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.NotificationChannel;

import java.util.UUID;

public interface IysReconciliationService {

    void reconcile(UUID userId, NotificationChannel channel);
}
