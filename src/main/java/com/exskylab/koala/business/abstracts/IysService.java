package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;

import java.util.UUID;
import java.util.List;

public interface IysService {

    void enqueue(User user, CommercialMessageConsent consent, NotificationChannel channel, boolean granted);

    boolean runTask(UUID taskId);

    List<UUID> findDueTaskIds();

    boolean isAllowed(User user, NotificationChannel channel);

    IysPermissionResult refreshPermission(User user, NotificationChannel channel);

    List<com.exskylab.koala.core.utilities.iys.IysReconciliationTarget> findStalePermissions();
}
