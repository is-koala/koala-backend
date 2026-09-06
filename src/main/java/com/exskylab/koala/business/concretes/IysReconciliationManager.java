package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysReconciliationService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IysReconciliationManager implements IysReconciliationService {

    private final IysService iysService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UserService userService;

    public IysReconciliationManager(IysService iysService,
                                    NotificationPreferenceService notificationPreferenceService,
                                    UserService userService) {
        this.iysService = iysService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void reconcile(UUID userId, NotificationChannel channel) {
        User user = userService.getById(userId);
        var permission = iysService.refreshPermission(user, channel);
        if (!permission.granted()) {
            notificationPreferenceService.applyIysWithdrawal(user, channel, permission.recordedAt());
        }
    }
}
