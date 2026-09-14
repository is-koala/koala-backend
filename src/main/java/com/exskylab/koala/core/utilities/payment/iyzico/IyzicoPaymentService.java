package com.exskylab.koala.core.utilities.payment.iyzico;

import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class IyzicoPaymentService {

    private static final Integer FORCE_THREE_D_SECURE = 1;

    private final RestTemplate restTemplate;
    private final IyzicoProperties iyzicoProperties;
    private final ObjectMapper objectMapper;

    public IyzicoPaymentService(RestTemplate restTemplate, IyzicoProperties iyzicoProperties,
                                ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.iyzicoProperties = iyzicoProperties;
        this.objectMapper = objectMapper;
    }

    public SubMerchantCreateResponseDto createSubMerchant(SubMerchantCreateRequestDto request) {
        log.info("iyzico: creating sub-merchant for externalId {}", request.getSubMerchantExternalId());

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", request.getSubMerchantExternalId());
        body.put("subMerchantExternalId", request.getSubMerchantExternalId());
        body.put("name", request.getName());
        body.put("email", request.getEmail());
        body.put("gsmNumber", request.getGsmNumber());
        body.put("address", request.getAddress());
        body.put("iban", request.getIban());
        body.put("currency", "TRY");

        switch (request.getSubMerchantType()) {
            case PERSONAL -> {
                body.put("subMerchantType", "PERSONAL");
                body.put("identityNumber", request.getIdentityNumber());
                body.put("contactName", request.getContactName());
                body.put("contactSurname", request.getContactSurname());
            }
            case PRIVATE_COMPANY -> {
                body.put("subMerchantType", "PRIVATE_COMPANY");
                body.put("identityNumber", request.getIdentityNumber());
                body.put("taxOffice", request.getTaxOffice());
            }
            case LIMITED_OR_JOINT_STOCK_COMPANY -> {
                body.put("subMerchantType", "LIMITED_OR_JOINT_STOCK_COMPANY");
                body.put("taxNumber", request.getTaxNumber());
                body.put("legalCompanyTitle", request.getLegalCompanyTitle());
                body.put("taxOffice", request.getTaxOffice());
            }
        }

        JsonNode root = call(HttpMethod.POST, IyzicoEndpoints.SUBMERCHANT, body, "sub-merchant creation");

        SubMerchantCreateResponseDto response = new SubMerchantCreateResponseDto();
        response.setStatus(root.path("status").asString());
        response.setSubMerchantKey(root.path("subMerchantKey").asString());

        log.info("iyzico: sub-merchant created for externalId {}", request.getSubMerchantExternalId());
        return response;
    }

    public SubMerchantUpdateResponseDto updateSubMerchant(SubMerchantUpdateRequestDto request) {
        log.info("iyzico: updating sub-merchant {}", request.getSubMerchantKey());

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", request.getSubMerchantKey());
        body.put("subMerchantKey", request.getSubMerchantKey());
        body.put("name", request.getName());
        body.put("email", request.getEmail());
        body.put("gsmNumber", request.getGsmNumber());
        body.put("address", request.getAddress());
        body.put("iban", request.getIban());
        body.put("contactName", request.getContactName());
        body.put("contactSurname", request.getContactSurname());
        body.put("currency", "TRY");
        body.put("identityNumber", request.getIdentityNumber());

        JsonNode root = call(HttpMethod.PUT, IyzicoEndpoints.SUBMERCHANT, body, "sub-merchant update");

        SubMerchantUpdateResponseDto response = new SubMerchantUpdateResponseDto();
        response.setStatus(root.path("status").asString());
        response.setSubMerchantKey(root.path("subMerchantKey").asString());
        return response;
    }

    public PaymentSessionResponseDto initiateCheckoutFormPayment(PaymentSessionRequestDto request) {
        log.info("iyzico: initiating checkout form for conversationId {} forceThreeDS={}",
                request.getConversationId(), iyzicoProperties.isForceThreeDSecure());

        request.setForceThreeDS(iyzicoProperties.isForceThreeDSecure() ? FORCE_THREE_D_SECURE : null);
        JsonNode root = call(HttpMethod.POST, IyzicoEndpoints.CHECKOUT_FORM_INITIALIZE, request,
                "payment session initiation");
        verifyResponseSignature(root, List.of("conversationId", "token"), Set.of());
        String responseConversationId = text(root, "conversationId");
        String responseToken = text(root, "token");
        if (!java.util.Objects.equals(request.getConversationId(), responseConversationId)
                || responseToken == null || responseToken.isBlank()
                || !isAllowedHostedCheckoutUrl(text(root, "paymentPageUrl"))) {
            throw new ExternalServiceException(PaymentMessages.PROVIDER_RESPONSE_INVALID);
        }

        PaymentSessionResponseDto response = new PaymentSessionResponseDto();
        response.setStatus(root.path("status").asString());
        response.setToken(root.path("token").asString());
        response.setPaymentPageUrl(root.path("paymentPageUrl").asString());
        response.setCheckoutFormContent(root.path("checkoutFormContent").asString());

        log.info("iyzico: checkout form initiated for conversationId {}", request.getConversationId());
        return response;
    }

    public IyzicoPaymentResultResponseDto retrieveCheckoutFormResult(IyzicoPaymentResultRequestDto request) {
        log.info("iyzico: retrieving checkout form result for conversationId {}", request.getConversationId());

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", request.getConversationId());
        body.put("token", request.getToken());

        JsonNode root = call(HttpMethod.POST, IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE, body,
                "payment result retrieval");
        verifyResponseSignature(root, List.of("paymentStatus", "paymentId", "currency", "basketId",
                "conversationId", "paidPrice", "price", "token"), Set.of("paidPrice", "price"));

        IyzicoPaymentResultResponseDto result = new IyzicoPaymentResultResponseDto();
        result.setStatus(root.path("status").asString());
        result.setPaymentId(text(root, "paymentId"));
        result.setConversationId(text(root, "conversationId"));
        result.setToken(text(root, "token"));
        result.setPaymentStatus(text(root, "paymentStatus"));
        result.setFraudStatus(integer(root, "fraudStatus"));
        result.setMdStatus(integer(root, "mdStatus"));
        result.setPrice(decimal(root, "price"));
        result.setPaidPrice(decimal(root, "paidPrice"));
        result.setCurrency(text(root, "currency"));
        result.setBasketId(text(root, "basketId"));

        JsonNode items = root.path("itemTransactions");
        result.setItemCount(items.isArray() ? items.size() : 0);
        if (result.getItemCount() > 0) {
            JsonNode first = items.get(0);
            result.setPaymentTransactionId(text(first, "paymentTransactionId"));
            result.setItemId(text(first, "itemId"));
            result.setItemPrice(decimal(first, "price"));
            result.setItemPaidPrice(decimal(first, "paidPrice"));
            result.setItemSubMerchantPrice(decimal(first, "subMerchantPrice"));
            result.setItemSubMerchantKey(text(first, "subMerchantKey"));
        }

        log.info("iyzico: payment result for conversationId {} is paymentStatus={} fraudStatus={} mdStatus={}",
                request.getConversationId(), result.getPaymentStatus(), result.getFraudStatus(),
                result.getMdStatus());
        return result;
    }

    public IyzicoApprovalResponseDto approvePaymentSettlement(String paymentTransactionId, String conversationId) {
        return itemDecision(IyzicoEndpoints.ITEM_APPROVE, paymentTransactionId, conversationId,
                "payment settlement approval");
    }

    public IyzicoApprovalResponseDto disapprovePaymentSettlement(String paymentTransactionId, String conversationId) {
        return itemDecision(IyzicoEndpoints.ITEM_DISAPPROVE, paymentTransactionId, conversationId,
                "payment settlement disapproval");
    }

    public IyzicoRefundResponseDto refund(IyzicoRefundRequestDto request) {
        log.info("iyzico: refunding {} on transaction {}", request.getPrice(), request.getPaymentTransactionId());

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", request.getConversationId());
        body.put("paymentTransactionId", request.getPaymentTransactionId());
        body.put("price", request.getPrice());
        body.put("currency", request.getCurrency());
        if (request.getIp() != null) {
            body.put("ip", request.getIp());
        }

        JsonNode root = call(HttpMethod.POST, IyzicoEndpoints.REFUND, body, "refund");

        IyzicoRefundResponseDto response = new IyzicoRefundResponseDto();
        response.setStatus(root.path("status").asString());
        response.setPaymentId(text(root, "paymentId"));
        response.setPaymentTransactionId(text(root, "paymentTransactionId"));
        response.setPrice(decimal(root, "price"));
        response.setCurrency(text(root, "currency"));
        response.setAuthCode(text(root, "authCode"));
        response.setHostReference(text(root, "hostReference"));
        response.setRefundHostReference(text(root, "refundHostReference"));

        log.info("iyzico: refunded {} on transaction {}", response.getPrice(), request.getPaymentTransactionId());
        return response;
    }

    public IyzicoRefundResponseDto cancel(String paymentId, String conversationId, String ip) {
        log.info("iyzico: cancelling payment {}", paymentId);

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", conversationId);
        body.put("paymentId", paymentId);
        if (ip != null) {
            body.put("ip", ip);
        }

        JsonNode root = call(HttpMethod.POST, IyzicoEndpoints.CANCEL, body, "cancel");

        IyzicoRefundResponseDto response = new IyzicoRefundResponseDto();
        response.setStatus(root.path("status").asString());
        response.setPaymentId(text(root, "paymentId"));
        response.setPrice(decimal(root, "price"));
        response.setCurrency(text(root, "currency"));
        response.setAuthCode(text(root, "authCode"));
        response.setHostReference(text(root, "hostReference"));
        return response;
    }

    private IyzicoApprovalResponseDto itemDecision(String endpoint, String paymentTransactionId,
                                                   String conversationId, String operation) {
        log.info("iyzico: {} for transaction {}", operation, paymentTransactionId);

        Map<String, Object> body = new HashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", conversationId);
        body.put("paymentTransactionId", paymentTransactionId);

        JsonNode root = call(HttpMethod.POST, endpoint, body, operation);

        return new IyzicoApprovalResponseDto(root.path("status").asString(),
                text(root, "paymentTransactionId"));
    }

    private JsonNode call(HttpMethod method, String endpoint, Object body, String operation) {
        String url = baseUrl() + endpoint;
        String responseBody;
        HttpStatusCode status;

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            String randomKey = newRandomKey();
            HttpHeaders headers = createIyzicoHeaders(endpoint, jsonBody, randomKey);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, method, new HttpEntity<>(jsonBody, headers), String.class);

            status = response.getStatusCode();
            responseBody = response.getBody();
        } catch (HttpStatusCodeException e) {
            status = e.getStatusCode();
            responseBody = e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("iyzico: {} could not reach the provider", operation, e);
            throw new ExternalServiceException(PaymentMessages.PROVIDER_UNAVAILABLE);
        }

        if (responseBody == null || responseBody.isBlank()) {
            log.error("iyzico: {} returned HTTP {} with an empty body", operation, status);
            throw new ExternalServiceException(PaymentMessages.PROVIDER_UNAVAILABLE);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("iyzico: {} returned HTTP {} with a non-JSON body", operation, status, e);
            throw new ExternalServiceException(PaymentMessages.PROVIDER_UNAVAILABLE);
        }

        if (!"success".equals(root.path("status").asString())) {
            log.error("iyzico: {} failed with HTTP {}, errorCode={}", operation, status,
                    text(root, "errorCode"));
            if (status.is5xxServerError() || status.value() == 408 || status.value() == 429) {
                throw new ExternalServiceException(PaymentMessages.PROVIDER_UNAVAILABLE);
            }
            throw new ExternalServiceException(PaymentMessages.PROVIDER_REJECTED);
        }

        return root;
    }

    private String baseUrl() {
        String configured = iyzicoProperties.getBaseUrl();
        if (configured == null) {
            return "";
        }

        String trimmed = configured.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private HttpHeaders createIyzicoHeaders(String uriPath, String jsonBody, String randomKey) throws Exception {
        String payload = (jsonBody == null || jsonBody.isEmpty()) ? uriPath : uriPath + jsonBody;
        String signature = generateIyzicoV2Signature(randomKey, payload);

        String authorizationString = "apiKey:" + iyzicoProperties.getApiKey()
                + "&randomKey:" + randomKey
                + "&signature:" + signature;

        String base64EncodedAuth = Base64.getEncoder().encodeToString(
                authorizationString.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "IYZWSv2 " + base64EncodedAuth);
        headers.set("x-iyzi-rnd", randomKey);

        return headers;
    }

    private static String newRandomKey() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateIyzicoV2Signature(String randomKey, String payload) {
        return hmacSha256(randomKey + payload);
    }

    private void verifyResponseSignature(JsonNode root, List<String> fields, java.util.Set<String> decimalFields) {
        String provided = text(root, "signature");
        try {
            String payload = fields.stream()
                    .map(field -> responseSignatureValue(root, field, decimalFields.contains(field)))
                    .reduce((left, right) -> left + ":" + right)
                    .orElse("");
            String expected = responseHmac(payload);
            if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                throw new ExternalServiceException(PaymentMessages.PROVIDER_RESPONSE_INVALID);
            }
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(PaymentMessages.PROVIDER_RESPONSE_INVALID);
        }
    }

    private String responseSignatureValue(JsonNode root, String field, boolean decimalField) {
        String value = text(root, field);
        if (value == null) {
            throw new ExternalServiceException(PaymentMessages.PROVIDER_RESPONSE_INVALID);
        }
        return decimalField ? new BigDecimal(value).stripTrailingZeros().toPlainString() : value;
    }

    private String responseHmac(String payload) {
        return hmacSha256(payload);
    }

    private String hmacSha256(String payload) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            sha256Hmac.init(new SecretKeySpec(
                    iyzicoProperties.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean isAllowedHostedCheckoutUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getFragment() != null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return switch (iyzicoProperties.providerEnvironment()) {
                case SANDBOX -> "https".equalsIgnoreCase(uri.getScheme())
                        && uri.getPort() == -1 && "sandbox-cpp.iyzipay.com".equals(host);
                case LIVE -> "https".equalsIgnoreCase(uri.getScheme())
                        && uri.getPort() == -1 && "cpp.iyzipay.com".equals(host);
                case LOOPBACK -> sameOrigin(uri, URI.create(baseUrl()));
                default -> false;
            };
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sameOrigin(URI candidate, URI configured) {
        return candidate.getScheme() != null
                && candidate.getScheme().equalsIgnoreCase(configured.getScheme())
                && candidate.getHost() != null
                && candidate.getHost().equalsIgnoreCase(configured.getHost())
                && candidate.getPort() == configured.getPort();
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
        return hexString.toString().toLowerCase(Locale.ROOT);
    }
}
