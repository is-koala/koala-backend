package com.exskylab.koala.core.configs;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class OutboundHttpClientTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void theOutboundClientIsNotTheJdkOne() {
        RestTemplate restTemplate = appConfig.restTemplate(
                new org.springframework.boot.restclient.RestTemplateBuilder(),
                appConfig.clientHttpRequestFactory());

        assertNotEquals(JdkClientHttpRequestFactory.class, restTemplate.getRequestFactory().getClass(),
                "iyzico answers the JDK HttpClient with an empty text/html 200; see CLAUDE.md 2b");
    }

    @Test
    void theOutboundClientCarriesTimeouts() {
        assertInstanceOf(SimpleClientHttpRequestFactory.class, appConfig.clientHttpRequestFactory());
    }

    @Test
    void theBuilderDefaultWouldHaveBeenTheBrokenOne() {
        RestTemplate plain = new org.springframework.boot.restclient.RestTemplateBuilder().build();

        assertEquals(JdkClientHttpRequestFactory.class, plain.getRequestFactory().getClass(),
                "if Boot changes its default again this test fails and the override can be revisited");
    }
}
