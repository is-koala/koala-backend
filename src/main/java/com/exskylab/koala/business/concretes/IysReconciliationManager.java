package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysReconciliationService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.utilities.iys.IysFailureClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class IysReconciliationManager implements IysReconciliationService {

    private final IysService iysService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    public IysReconciliationManager(IysService iysService,
                                    NotificationPreferenceService notificationPreferenceService,
                                    UserService userService,
                                    PlatformTransactionManager transactionManager) {
        this.iysService = iysService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.userService = userService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void reconcile(UUID userId, NotificationChannel channel) {
        User user = userService.getById(userId);
        final com.exskylab.koala.core.utilities.iys.IysPermissionResult permission;
        try {
            permission = iysService.refreshPermission(user, channel);
        } catch (RuntimeException exception) {
            iysService.recordReconciliationFailure(userId, channel,
                    IysFailureClassifier.classify(exception));
            throw exception;
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (iysService.applyReconciledPermission(user, permission)) {
                notificationPreferenceService.applyIysWithdrawal(user, channel, permission.recordedAt());
            }
        });
    }
}
