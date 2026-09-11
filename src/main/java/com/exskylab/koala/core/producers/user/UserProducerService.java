package com.exskylab.koala.core.producers.user;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.helpers.AddressFormatter;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserProducerService {

    private final UserProfileSyncService userProfileSyncService;
    private final BankAccountService bankAccountService;

    public UserProducerService(UserProfileSyncService userProfileSyncService,
                               BankAccountService bankAccountService) {
        this.userProfileSyncService = userProfileSyncService;
        this.bankAccountService = bankAccountService;
    }

    public void publishUserProfileUpdated(User updatedUser) {
        log.info("Accepting user profile sync intent for user id: {}", updatedUser.getId());
        userProfileSyncService.enqueue(convertToEventDto(updatedUser));
    }

    private UserUpdatedEventDto convertToEventDto(User updatedUser) {
        String fullAddress = "";
        if (updatedUser.getAddress() != null) {
            fullAddress = AddressFormatter.displayAddress(updatedUser.getAddress());
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
