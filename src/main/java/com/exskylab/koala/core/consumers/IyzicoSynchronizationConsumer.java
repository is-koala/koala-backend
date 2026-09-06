package com.exskylab.koala.core.consumers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantUpdateRequestDto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class IyzicoSynchronizationConsumer {

    private final IyzicoPaymentService iyzicoPaymentService;


    public IyzicoSynchronizationConsumer(IyzicoPaymentService iyzicoPaymentService) {
        this.iyzicoPaymentService = iyzicoPaymentService;
    }



    @RabbitListener(queues = RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE,
            containerFactory = "iyzicoListenerContainerFactory")
    public void handleUserProfileUpdatedForSync(UserUpdatedEventDto userDto){
        log.info("Handling user profile updated for sync event for id: {}", userDto.getId());
        if (userDto.getIyzicoSubMerchantKey() == null || userDto.getIyzicoSubMerchantKey().isEmpty()) {
            log.warn("User with id: {} does not have an Iyzico sub-merchant key. Skipping synchronization with iyzico.", userDto.getId());
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
        subMerchantUpdateRequestDto.setIdentityNumber(userDto.getIdentityNumber());
        subMerchantUpdateRequestDto.setIban(userDto.getIban());
        try {
            iyzicoPaymentService.updateSubMerchant(subMerchantUpdateRequestDto);
        }catch (Exception e){
            log.error("Failed to update Iyzico sub-merchant for user id: {}", userDto.getId(), e);
            throw e;
        }


    }
}
