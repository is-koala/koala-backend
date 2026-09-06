package com.exskylab.koala.core.utilities.sms.iletiMerkezi;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.constants.SmsMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.IletiMerkeziProperties;
import com.exskylab.koala.core.utilities.sms.SmsService;
import com.exskylab.koala.core.utilities.sms.iletiMerkezi.request.*;
import com.exskylab.koala.core.utilities.sms.iletiMerkezi.response.IletiMerkeziSendSmsResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
@Slf4j
public class IletiMerkeziSmsHelper implements SmsService {

    private final RestTemplate restTemplate;
    private final IletiMerkeziProperties properties;

    public IletiMerkeziSmsHelper(RestTemplate restTemplate, IletiMerkeziProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public String sendSms(String[] phoneNumbers, String messageText, boolean isIys) {
        log.info("Sending sms to: {}", LogMasks.phones(phoneNumbers));
        String apiUrl = "https://api.iletimerkezi.com/v1/send-sms/json";
        Authentication authentication = new Authentication(properties.getApiKey(), properties.getApiHash());
        Recipients recipients = new Recipients(phoneNumbers);
        Message message = new Message(messageText, recipients);

        Order order = new Order(
                properties.getSender(),
                isIys ? "1" : "0",
                isIys ? "BIREYSEL" : "",
                message
        );

        Request requestBody = new Request(authentication, order);
        IletiMerkeziSmsRequest smsRequest = new IletiMerkeziSmsRequest(requestBody);


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<IletiMerkeziSmsRequest> entity = new HttpEntity<>(smsRequest, headers);

        try{
            IletiMerkeziSendSmsResponse response = restTemplate.postForObject(apiUrl, entity, IletiMerkeziSendSmsResponse.class);

           if (response != null && response.getStatus().getCode().equals("200")){
               log.info("Sms sent successfully to: {} Order ID: {}", LogMasks.phones(phoneNumbers), response.getOrder().getId());
               return response.getOrder().getId();
           }else {
                log.error("Failed to send sms to: {}. Response: {}", LogMasks.phones(phoneNumbers), response != null ? response.getStatus().getMessage() : "No response");
               throw new ExternalServiceException(SmsMessages.SMS_SEND_FAILED);
           }
        }catch (Exception e){
            log.error("Error occurred while sending sms to: {}", LogMasks.phones(phoneNumbers), e);
            throw new ExternalServiceException(SmsMessages.SMS_SEND_FAILED);
        }
    }




}
