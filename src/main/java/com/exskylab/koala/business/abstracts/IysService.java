package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.entities.IysFailureCode;

import java.util.UUID;
import java.util.List;

public interface IysService {

    void enqueue(User user, CommercialMessageConsent consent, NotificationChannel channel, boolean granted,
                 ClientContext client);

    boolean runTask(UUID taskId);

    List<UUID> findDueTaskIds();

    boolean isAllowed(User user, NotificationChannel channel, String destination);

    IysPermissionResult refreshPermission(User user, NotificationChannel channel);

    boolean applyReconciledPermission(User user, IysPermissionResult permission);

    List<com.exskylab.koala.core.utilities.iys.IysReconciliationTarget> findStalePermissions();

    void recordReconciliationFailure(UUID userId, NotificationChannel channel, IysFailureCode failureCode);
}
