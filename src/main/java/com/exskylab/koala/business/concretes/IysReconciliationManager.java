package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysReconciliationService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.IysFailureCode;
import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import org.springframework.stereotype.Service;

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
    public void reconcile(UUID userId, NotificationChannel channel) {
        User user = userService.getById(userId);
        final com.exskylab.koala.core.utilities.iys.IysPermissionResult permission;
        try {
            permission = iysService.refreshPermission(user, channel);
        } catch (RuntimeException exception) {
            iysService.recordReconciliationFailure(userId, channel,
                    exception instanceof ExternalServiceException external
                            ? failureCode(external) : IysFailureCode.UNKNOWN);
            throw exception;
        }
        if (!permission.granted()) {
            notificationPreferenceService.applyIysWithdrawal(user, channel, permission.recordedAt());
        }
    }

    private IysFailureCode failureCode(ExternalServiceException exception) {
        return switch (exception.getMessage()) {
            case IysMessages.PROVIDER_UNAVAILABLE -> IysFailureCode.PROVIDER_UNAVAILABLE;
            case IysMessages.PROVIDER_REJECTED -> IysFailureCode.PROVIDER_REJECTED;
            case IysMessages.INVALID_RESPONSE -> IysFailureCode.INVALID_RESPONSE;
            default -> IysFailureCode.UNKNOWN;
        };
    }
}
