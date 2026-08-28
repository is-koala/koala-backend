package com.exskylab.koala.core.utilities.payment;

import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoEndpoints;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoRefundRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IyzicoProtocolTest {

    private static final String BASE_URL = "https://sandbox-api.iyzipay.com";
    private static final String SECRET = "sandbox-secret";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private IyzicoPaymentService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        IyzicoProperties properties = new IyzicoProperties();
        properties.setApiKey("sandbox-api-key");
        properties.setSecretKey(SECRET);
        properties.setBaseUrl(BASE_URL);

        service = new IyzicoPaymentService(restTemplate, properties, new ObjectMapper());
    }

    private IyzicoPaymentResultRequestDto retrieveRequest() {
        IyzicoPaymentResultRequestDto request = new IyzicoPaymentResultRequestDto();
        request.setToken("tok-1");
        request.setConversationId("conv-1");
        return request;
    }

    private static String successBody() {
        return """
                {"status":"success","paymentStatus":"SUCCESS","fraudStatus":1,
                 "paymentId":"pay-1","conversationId":"conv-1","price":"1075.00",
                 "paidPrice":"1075.00","currency":"TRY","basketId":"basket-1",
                 "itemTransactions":[{"paymentTransactionId":"txn-1","paidPrice":"1075.00"}]}
                """;
    }

    @Test
    void theCheckoutFormResultIsRetrievedFromTheDocumentedEndpoint() {
        server.expect(requestTo(BASE_URL + "/payment/iyzipos/checkoutform/auth/ecom/detail"))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        service.retrieveCheckoutFormResult(retrieveRequest());

        server.verify();
    }

    @Test
    void theDocumentedEndpointPathsAreNotDriftedFrom() {
        assertEquals("/payment/iyzipos/checkoutform/initialize/auth/ecom",
                IyzicoEndpoints.CHECKOUT_FORM_INITIALIZE);
        assertEquals("/payment/iyzipos/checkoutform/auth/ecom/detail", IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE);
        assertEquals("/payment/iyzipos/item/approve", IyzicoEndpoints.ITEM_APPROVE);
        assertEquals("/payment/iyzipos/item/disapprove", IyzicoEndpoints.ITEM_DISAPPROVE);
        assertEquals("/payment/refund", IyzicoEndpoints.REFUND);
        assertEquals("/payment/cancel", IyzicoEndpoints.CANCEL);
        assertEquals("/onboarding/submerchant", IyzicoEndpoints.SUBMERCHANT);
    }

    @Test
    void everyRequestCarriesTheRandomKeyInItsOwnHeader() {
        server.expect(request -> {
            String randomKey = request.getHeaders().getFirst("x-iyzi-rnd");
            assertNotNull(randomKey, "x-iyzi-rnd is required on every iyzico call");
            assertFalse(randomKey.isBlank());
            assertTrue(request.getHeaders().getFirst("Authorization").startsWith("IYZWSv2 "));
        }).andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        service.retrieveCheckoutFormResult(retrieveRequest());

        server.verify();
    }

    @Test
    void theSignatureIsBuiltFromTheRandomKeyThePathAndTheBody() {
        server.expect(request -> {
            String randomKey = request.getHeaders().getFirst("x-iyzi-rnd");
            String authorization = new String(Base64.getDecoder().decode(
                    request.getHeaders().getFirst("Authorization").substring("IYZWSv2 ".length())),
                    StandardCharsets.UTF_8);

            assertTrue(authorization.contains("&randomKey:" + randomKey),
                    "the header random key must be the one that was signed");

            String body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsBytes(), StandardCharsets.UTF_8);
            String expected = hmac(randomKey + IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE + body);

            assertTrue(authorization.endsWith("&signature:" + expected));
        }).andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        service.retrieveCheckoutFormResult(retrieveRequest());

        server.verify();
    }

    @Test
    void aDeclinedPaymentIsReturnedRatherThanThrown() {
        server.expect(requestTo(BASE_URL + IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE))
                .andRespond(withSuccess("""
                        {"status":"success","paymentStatus":"FAILURE","conversationId":"conv-1"}
                        """, MediaType.APPLICATION_JSON));

        IyzicoPaymentResultResponseDto result = service.retrieveCheckoutFormResult(retrieveRequest());

        assertEquals("FAILURE", result.getPaymentStatus());
    }

    @Test
    void theLineItemTransactionIdAndPaidPriceAreReadFromTheFirstItem() {
        server.expect(requestTo(BASE_URL + IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        IyzicoPaymentResultResponseDto result = service.retrieveCheckoutFormResult(retrieveRequest());

        assertEquals("txn-1", result.getPaymentTransactionId());
        assertEquals(0, new BigDecimal("1075.00").compareTo(result.getItemPaidPrice()));
        assertEquals(0, new BigDecimal("1075.00").compareTo(result.getPaidPrice()));
        assertEquals(1, result.getFraudStatus());
        assertEquals("TRY", result.getCurrency());
    }

    @Test
    void aProviderFailureBodyBecomesAnExternalServiceError() {
        server.expect(requestTo(BASE_URL + IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE))
                .andRespond(withSuccess("""
                        {"status":"failure","errorCode":"5088","errorMessage":"Token gecersiz"}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(ExternalServiceException.class, () -> service.retrieveCheckoutFormResult(retrieveRequest()));
    }

    @Test
    void anErrorBodyReturnedWithFourHundredIsStillRead() {
        server.expect(requestTo(BASE_URL + IyzicoEndpoints.CHECKOUT_FORM_RETRIEVE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"failure","errorCode":"1000","errorMessage":"Gecersiz istek"}
                                """));

        assertThrows(ExternalServiceException.class, () -> service.retrieveCheckoutFormResult(retrieveRequest()));
    }

    @Test
    void theRefundIsSentAsADecimalPriceOnTheItemTransaction() {
        IyzicoRefundRequestDto request = new IyzicoRefundRequestDto();
        request.setPaymentTransactionId("txn-1");
        request.setConversationId("conv-1");
        request.setPrice(new BigDecimal("537.50"));

        server.expect(requestTo(BASE_URL + "/payment/refund"))
                .andExpect(jsonPath("$.paymentTransactionId").value("txn-1"))
                .andExpect(jsonPath("$.price").value(537.50))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andRespond(withSuccess("""
                        {"status":"success","paymentId":"pay-1","paymentTransactionId":"txn-1",
                         "price":"537.50","currency":"TRY"}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(0, new BigDecimal("537.50").compareTo(service.refund(request).getPrice()));

        server.verify();
    }

    @Test
    void theItemApprovalCarriesTheCallersConversationId() {
        server.expect(requestTo(BASE_URL + "/payment/iyzipos/item/approve"))
                .andExpect(jsonPath("$.paymentTransactionId").value("txn-1"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andRespond(withSuccess("""
                        {"status":"success","paymentTransactionId":"txn-1"}
                        """, MediaType.APPLICATION_JSON));

        service.approvePaymentSettlement("txn-1", "conv-1");

        server.verify();
    }

    private static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
