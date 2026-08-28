package com.exskylab.koala.core.producers.user;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.entities.User;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserProducerService {

    private final RabbitTemplate rabbitTemplate;
    private final BankAccountService bankAccountService;

    public UserProducerService(RabbitTemplate rabbitTemplate, BankAccountService bankAccountService) {
        this.rabbitTemplate = rabbitTemplate;
        this.bankAccountService = bankAccountService;
    }


    public void publishUserProfileUpdated(User updatedUser) {
        log.info("Publishing user profile updated event for user id: {}", updatedUser.getId());

        UserUpdatedEventDto eventDto = convertToEventDto(updatedUser);


        try{
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.USER_EXCHANGE,
                    RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY,
                    eventDto
            );
            log.info("User profile updated event sent for user id: {}", updatedUser.getId());
        }catch (Exception e){
            log.error("User profile updated event could not be sent for user id: {}, Error: {}",
                    updatedUser.getId(),
                    e.getMessage());
        }

    }

    private UserUpdatedEventDto convertToEventDto(User updatedUser) {
        String fullAddress = "";
        if (updatedUser.getAddress() != null) {
            fullAddress = String.format("%s, %s, %s, %s, %s",
                    updatedUser.getAddress().getAddressLine(),
                    updatedUser.getAddress().getCity().getName(),
                    updatedUser.getAddress().getDistrict().getName(),
                    updatedUser.getAddress().getCountry().getName(),
                    updatedUser.getAddress().getPostalCode());
        }


        UserUpdatedEventDto eventDto = new UserUpdatedEventDto();
        eventDto.setId(updatedUser.getId());
        eventDto.setFirstName(updatedUser.getFirstName());
        eventDto.setLastName(updatedUser.getLastName());
        eventDto.setEmail(updatedUser.getEmail());
        eventDto.setGsmNumber(updatedUser.getPhoneNumber());
        eventDto.setAddress(fullAddress);
        eventDto.setIban(bankAccountService.findPrimaryIban(updatedUser.getId()).orElse(null));
        eventDto.setIdentityNumber(updatedUser.getTcIdentityNumber());
        eventDto.setIyzicoSubMerchantKey(updatedUser.getIyzicoSubmerchantKey());

        return eventDto;
    }


}
