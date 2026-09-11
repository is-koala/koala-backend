package com.exskylab.koala.core.consumers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantUpdateRequestDto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class IyzicoSynchronizationConsumer {

    private final IyzicoPaymentService iyzicoPaymentService;
    private final UserProfileSyncService userProfileSyncService;

    public IyzicoSynchronizationConsumer(IyzicoPaymentService iyzicoPaymentService,
                                         UserProfileSyncService userProfileSyncService) {
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.userProfileSyncService = userProfileSyncService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE,
            containerFactory = "iyzicoListenerContainerFactory")
    public void handleUserProfileUpdatedForSync(UserUpdatedEventDto userDto) {
        log.info("Handling user profile updated for sync event for id: {}", userDto.getId());
        Optional<UUID> processingClaim = claimIfTracked(userDto);
        if (userDto.getIntentId() != null && processingClaim.isEmpty()) {
            log.info("Skipping duplicate Iyzico profile sync for intent {}", userDto.getIntentId());
            return;
        }

        try {
            if (userDto.getIyzicoSubMerchantKey() == null || userDto.getIyzicoSubMerchantKey().isEmpty()) {
                log.warn("User with id: {} does not have an Iyzico sub-merchant key. Skipping synchronization with iyzico.",
                        userDto.getId());
                completeIfTracked(userDto, processingClaim);
                return;
            }

            SubMerchantUpdateRequestDto subMerchantUpdateRequestDto = new SubMerchantUpdateRequestDto();
            subMerchantUpdateRequestDto.setSubMerchantKey(userDto.getIyzicoSubMerchantKey());
            subMerchantUpdateRequestDto.setName(userDto.getFirstName() + " " + userDto.getLastName());
            subMerchantUpdateRequestDto.setEmail(userDto.getEmail());
            subMerchantUpdateRequestDto.setGsmNumber(userDto.getGsmNumber());
            subMerchantUpdateRequestDto.setAddress(userDto.getAddress());
            subMerchantUpdateRequestDto.setIdentityNumber(userDto.getIdentityNumber());
            subMerchantUpdateRequestDto.setContactName(userDto.getFirstName());
            subMerchantUpdateRequestDto.setContactSurname(userDto.getLastName());
            subMerchantUpdateRequestDto.setIban(userDto.getIban());
            iyzicoPaymentService.updateSubMerchant(subMerchantUpdateRequestDto);
            completeIfTracked(userDto, processingClaim);
        } catch (RuntimeException exception) {
            log.error("Failed to update Iyzico sub-merchant for user id: {}", userDto.getId(), exception);
            processingClaim.ifPresent(claimId ->
                    userProfileSyncService.releaseProcessingClaim(userDto.getIntentId(), claimId));
            throw exception;
        }
    }

    private Optional<UUID> claimIfTracked(UserUpdatedEventDto userDto) {
        if (userDto.getIntentId() == null) {
            return Optional.empty();
        }
        return userProfileSyncService.claimForProcessing(userDto.getIntentId());
    }

    private void completeIfTracked(UserUpdatedEventDto userDto, Optional<UUID> processingClaim) {
        processingClaim.ifPresent(claimId ->
                userProfileSyncService.completeProcessing(userDto.getIntentId(), claimId));
    }
}
