package com.exskylab.koala.core.utilities.payment.iyzico;

import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class IyzicoPaymentService {

    private final RestTemplate restTemplate;

    private final IyzicoProperties iyzicoProperties;

    private final ObjectMapper objectMapper;

    private final Logger logger = LoggerFactory.getLogger(IyzicoPaymentService.class);

    public IyzicoPaymentService(RestTemplate restTemplate, IyzicoProperties iyzicoProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.iyzicoProperties = iyzicoProperties;
        this.objectMapper = objectMapper;
    }


    public SubMerchantCreateResponseDto createSubMerchant(SubMerchantCreateRequestDto request){
        String endpoint = "/onboarding/submerchant";
        String url = iyzicoProperties.getBaseUrl()+endpoint;
        logger.info("iyzico: Creating sub-merchant for externalId: {}", request.getSubMerchantExternalId());

        try{

            Map<String, Object> iyzicoRequestBody = new HashMap<>();

            iyzicoRequestBody.put("locale", "tr");
            iyzicoRequestBody.put("conversationId", UUID.randomUUID().toString());
            iyzicoRequestBody.put("subMerchantExternalId", request.getSubMerchantExternalId());
            iyzicoRequestBody.put("name", request.getName());
            iyzicoRequestBody.put("email", request.getEmail());
            iyzicoRequestBody.put("gsmNumber", request.getGsmNumber());
            iyzicoRequestBody.put("address", request.getAddress());
            iyzicoRequestBody.put("iban", request.getIban());
            iyzicoRequestBody.put("currency", "TRY");

            switch (request.getSubMerchantType()){
                case PERSONAL:
                    iyzicoRequestBody.put("subMerchantType", "PERSONAL");
                    iyzicoRequestBody.put("identityNumber", request.getIdentityNumber());
                    iyzicoRequestBody.put("contactName", request.getContactName());
                    iyzicoRequestBody.put("contactSurname", request.getContactSurname());
                    break;
                case PRIVATE_COMPANY:
                    iyzicoRequestBody.put("subMerchantType", "PRIVATE_COMPANY");
                    iyzicoRequestBody.put("identityNumber", request.getIdentityNumber());
                    iyzicoRequestBody.put("taxOffice", request.getTaxOffice());
                    break;
                case LIMITED_OR_JOINT_STOCK_COMPANY:
                    iyzicoRequestBody.put("subMerchantType", "LIMITED_OR_JOINT_STOCK_COMPANY");
                    iyzicoRequestBody.put("taxNumber", request.getTaxNumber());
                    iyzicoRequestBody.put("legalCompanyTitle", request.getLegalCompanyTitle());
                    iyzicoRequestBody.put("taxOffice", request.getTaxOffice());
                    break;
            }

            String jsonBody = objectMapper.writeValueAsString(iyzicoRequestBody);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);


            JsonNode root = objectMapper.readTree(response.getBody());

            if ("success".equals(root.path("status").asText())){
                SubMerchantCreateResponseDto responseDto = objectMapper.treeToValue(root, SubMerchantCreateResponseDto.class);

                logger.info("iyzico: Sub-merchant created successfully for externalId: {}. Key: {}",
                        request.getSubMerchantExternalId(), responseDto.getSubMerchantKey());

                return responseDto;
            }else {
                String errorMessage = root.path("errorMessage").asText();
                logger.error("iyzico: Failed to create sub-merchant for externalId: {}. Error: {}",
                        request.getSubMerchantExternalId(), errorMessage);
                throw new RuntimeException("Failed to create sub-merchant: " + errorMessage);
            }

        }catch (Exception e){
            logger.error("iyzico: Exception during sub-merchant creation for externalId: {}. Exception: {}", request.getSubMerchantExternalId(), e.getMessage());
            throw new RuntimeException("Exception during sub-merchant creation: " + e.getMessage(), e);
        }

    }


    public SubMerchantUpdateResponseDto updateSubMerchant(SubMerchantUpdateRequestDto request){

        String endpoint = "/onboarding/submerchant";
        String url = iyzicoProperties.getBaseUrl()+endpoint;
        logger.info("iyzico: Updating sub-merchant for externalId: {}", request.getSubMerchantKey());

        try{


            Map<String, Object> iyzicoRequestBody = convertUpdateRequestToIyzicoMap(request);

            String jsonBody = objectMapper.writeValueAsString(iyzicoRequestBody);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            if ("success".equals(root.path("status").asText())){
                SubMerchantUpdateResponseDto responseDto = objectMapper.treeToValue(root, SubMerchantUpdateResponseDto.class);
                logger.info("iyzico: Sub-merchant with key {} updated successfully.", request.getSubMerchantKey());
                return responseDto;
            }else {
                String errorMessage = root.path("errorMessage").asText();
                logger.error("iyzico: Failed to update sub-merchant with key {}. Error: {}",
                        request.getSubMerchantKey(), errorMessage);
                throw new RuntimeException("Failed to update sub-merchant: " + errorMessage);
            }


        }catch (Exception e){
            logger.error("iyzico: Exception during sub-merchant update for externalId: {}. Exception: {}", request.getSubMerchantKey(), e.getMessage());
            throw new RuntimeException("Exception during sub-merchant update: " + e.getMessage(), e);
        }

    }



    public PaymentSessionResponseDto initiateCheckoutFormPayment(PaymentSessionRequestDto request){

        String endpoint = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
        String url = iyzicoProperties.getBaseUrl()+endpoint;
        logger.info("iyzico: Initiating checkout form payment for conversationId: {}", request.getConversationId());


        try{
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if ("success".equals(root.path("status").asText())){
                PaymentSessionResponseDto sessionResponseDto = objectMapper.treeToValue(root, PaymentSessionResponseDto.class);

                logger.info("iyzico: Checkout form payment initiated successfully for conversationId: {}.",
                        request.getConversationId());


                return sessionResponseDto;
            } else {
                String errorMessage = root.path("errorMessage").asText();
                throw new RuntimeException("Failed to initiate payment session: " + errorMessage);
            }

        } catch (Exception e){
            logger.error("iyzico: Exception during payment session initiation for conversationId: {}. Exception: {}", request.getConversationId(), e.getMessage());
            throw new RuntimeException("Exception during payment session initiation: " + e.getMessage(), e);
        }


    }


    public IyzicoPaymentResultResponseDto retrieveCheckoutFormResult(IyzicoPaymentResultRequestDto request){

        String endpoint = "/payment/checkout-form/retrieve";
        String url = iyzicoProperties.getBaseUrl()+endpoint;
        logger.info("iyzico: Retrieving checkout form result for conversationId: {}", request.getConversationId());

        try{
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if ("success".equals(root.path("status").asText())){
                if (!"SUCCESS".equals(root.path("paymentStatus").asText())){
                    logger.error("iyzico: Payment failed for conversationId: {}. PaymentStatus: {}",
                            request.getConversationId(), root.path("paymentStatus").asText());
                    throw new RuntimeException("Payment failed with status: " + root.path("paymentStatus").asText());
                }
                IyzicoPaymentResultResponseDto responseDto = objectMapper.treeToValue(root, IyzicoPaymentResultResponseDto.class);
                String transactionId = root.path("itemTransactions").get(0).path("paymentTransactionId").asText();
                responseDto.setPaymentTransactionId(transactionId);

                logger.info("iyzico: Payment successful for conversationId: {}. TransactionId: {}", request.getConversationId(), transactionId);

                return responseDto;
            }else {
                String errorMessage = root.path("errorMessage").asText();
                logger.error("iyzico: Failed to retrieve payment result for conversationId: {}. Error: {}",
                        request.getConversationId(), errorMessage);
                throw new RuntimeException("Failed to retrieve payment result: " + errorMessage);
            }

        } catch (Exception e){
            logger.error("iyzico: Exception during payment result retrieval for conversationId: {}. Exception: {}", request.getConversationId(), e.getMessage());
            throw new RuntimeException("Exception during payment result retrieval: " + e.getMessage(), e);
        }

    }




    public IyzicoApprovalResponseDto approvePaymentSettlement(String paymentTransactionId){
        String endpoint = "/payment/iyzipos/item/approve";
        String url = iyzicoProperties.getBaseUrl()+endpoint;

        logger.info("iyzico: Approving payment settlement for transactionId: {}", paymentTransactionId);


        try{
            Map<String, String> requestBody = Map.of(
                    "locale", "tr",
                    "conversationId", UUID.randomUUID().toString(),
                    "paymentTransactionId", paymentTransactionId
            );
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if ("success".equals(root.path("status").asText())){
                return objectMapper.treeToValue(root, IyzicoApprovalResponseDto.class);
            }else {
                String errorMessage = root.path("errorMessage").asText();
                logger.error("iyzico: Failed to approve payment settlement for transactionId: {}. Error: {}",
                        paymentTransactionId, errorMessage);
                throw new RuntimeException("Failed to approve payment settlement: " + errorMessage);


            }
        }catch (Exception e){
            logger.error("iyzico: Exception during payment settlement approval for transactionId: {}. Exception: {}",
                    paymentTransactionId, e.getMessage());

            throw new RuntimeException("Exception during payment settlement approval: " + e.getMessage(), e);
        }

    }

    public IyzicoApprovalResponseDto disapprovePaymentSettlement(String paymentTransactionId){
        String endpoint = "/payment/iyzipos/item/disapprove";
        String url = iyzicoProperties.getBaseUrl()+endpoint;

        logger.info("iyzico: Disapproving payment settlement for transactionId: {}", paymentTransactionId);


        try{
            Map<String, String> requestBody = Map.of(
                    "locale", "tr",
                    "conversationId", UUID.randomUUID().toString(),
                    "paymentTransactionId", paymentTransactionId
            );
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if ("success".equals(root.path("status").asText())){
                return objectMapper.treeToValue(root, IyzicoApprovalResponseDto.class);
            }else {
                String errorMessage = root.path("errorMessage").asText();
                logger.error("iyzico: Failed to disapprove payment settlement for transactionId: {}. Error: {}",
                        paymentTransactionId, errorMessage);
                throw new RuntimeException("Failed to approve payment settlement: " + errorMessage);


            }
        }catch (Exception e){
            logger.error("iyzico: Exception during payment settlement disapprovement for transactionId: {}. Exception: {}",
                    paymentTransactionId, e.getMessage());

            throw new RuntimeException("Exception during payment settlement disapprove: " + e.getMessage(), e);
        }

    }



    private HttpHeaders createIyzicoHeaders(String uriPath, String jsonBody) throws Exception {
        String randomKey = String.valueOf(System.currentTimeMillis());

        String payload = (jsonBody == null || jsonBody.isEmpty()) ? uriPath : uriPath+jsonBody;

        String signature= generateIyzicoV2Signature(randomKey, payload);

        String authorizationString = "apiKey:" + iyzicoProperties.getApiKey()
                + "&randomKey:" + randomKey
                + "&signature:" + signature;

        String base64EncodedAuth = Base64.getEncoder().encodeToString(
                authorizationString.getBytes(StandardCharsets.UTF_8)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "IYZWSv2 " + base64EncodedAuth);

        return headers;

    }


    private Map<String, Object> convertUpdateRequestToIyzicoMap(SubMerchantUpdateRequestDto dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("locale", "tr");
        map.put("conversationId", UUID.randomUUID().toString());
        map.put("subMerchantKey", dto.getSubMerchantKey());

        map.put("name", dto.getName());
        map.put("email", dto.getEmail());
        map.put("gsmNumber", dto.getGsmNumber());
        map.put("address", dto.getAddress());
        map.put("iban", dto.getIban());
        map.put("contactName", dto.getContactName());
        map.put("contactSurname", dto.getContactSurname());
        map.put("currency", "TRY");
        map.put("identityNumber", dto.getIdentityNumber());

        return map;

    }





    private String generateIyzicoV2Signature(String randomKey, String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        String secretKey = iyzicoProperties.getSecretKey();
        String dataToEncrypt = randomKey+payload;

        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key_spec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secret_key_spec);

        byte[] hmac_sha256_bytes = sha256Hmac.doFinal(dataToEncrypt.getBytes(StandardCharsets.UTF_8));

        return bytesToHex(hmac_sha256_bytes);

    }

    private static String bytesToHex(byte[] hash) {

        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();

    }


}
