package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.entities.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IletiMerkeziIysClientTest {

    private IysProperties properties;
    private IletiMerkeziIysClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = configuredProperties();
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new IletiMerkeziIysClient(restTemplate, properties);
    }

    @Test
    void registrationUsesTheProviderConsentEnvelope() {
        server.expect(requestTo("https://provider.test/v1/consent/create/json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"request":{"authentication":{"key":"key","hash":"hash"},"consent":{
                          "brandCode":"brand","list":[{"recipient":"person@koala.test",
                          "recipientType":"BIREYSEL","type":"EPOSTA","status":"ONAY",
                          "source":"HS_WEB","consentDate":"2026-09-06 21:00:00"}]}}}
                        """))
                .andRespond(withSuccess("""
                        {"response":{"status":{"code":200,"message":"İşlem başarılı"}}}
                        """, MediaType.APPLICATION_JSON));

        client.registerConsent(new IysConsentRequest("person@koala.test", NotificationChannel.EMAIL,
                true, Instant.parse("2026-09-06T18:00:00Z"), "HS_WEB"));

        server.verify();
    }

    @Test
    void lookupReturnsTheCurrentRegistryDecision() {
        server.expect(requestTo("https://provider.test/v1/consent/show/json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"request":{"authentication":{"key":"key","hash":"hash"},"consent":{
                          "brandCode":"brand","recipient":"person@koala.test",
                          "recipientType":"BIREYSEL","type":"EPOSTA"}}}
                        """))
                .andRespond(withSuccess("""
                        {"response":{"status":{"code":200,"message":"İşlem başarılı"},"consent":{
                          "brandCode":"brand","recipient":"person@koala.test","recipientType":"BIREYSEL",
                          "type":"EPOSTA","status":"RET","source":"HS_WEB",
                          "consentDate":"2026-09-06 20:45:00"}}}
                        """, MediaType.APPLICATION_JSON));

        IysPermissionResult permission = client.lookupConsent("person@koala.test", NotificationChannel.EMAIL);

        assertFalse(permission.granted());
        assertEquals(Instant.parse("2026-09-06T17:45:00Z"), permission.recordedAt());
        assertEquals("HS_WEB", permission.source());
        server.verify();
    }

    @Test
    void missingCredentialsFailBeforeAnyProviderRequest() {
        properties.setApiHash("");

        assertFalse(properties.isConfigured());
        assertThrows(ExternalServiceException.class,
                () -> client.lookupConsent("person@koala.test", NotificationChannel.EMAIL));
        server.verify();
    }

    private IysProperties configuredProperties() {
        IysProperties configured = new IysProperties();
        configured.setEnabled(true);
        configured.setApiUrl("https://provider.test/v1/consent");
        configured.setApiKey("key");
        configured.setApiHash("hash");
        configured.setBrandCode("brand");
        assertTrue(configured.isConfigured());
        return configured;
    }
}
