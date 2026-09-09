package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotificationProviderConfigTest {

    @Test
    void smsConnectAndReadOperationsUseTheConfiguredProviderBudget() {
        NotificationDispatchProperties properties = properties(Duration.ofMillis(1234));

        RestTemplate template = new NotificationProviderConfig()
                .notificationRestTemplate(new RestTemplateBuilder(), properties);

        SimpleClientHttpRequestFactory factory = assertInstanceOf(SimpleClientHttpRequestFactory.class,
                template.getRequestFactory());
        assertEquals(1234, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(1234, ReflectionTestUtils.getField(factory, "readTimeout"));
    }

    @Test
    void smtpConnectReadAndWriteOperationsUseTheConfiguredProviderBudget() {
        NotificationDispatchProperties properties = properties(Duration.ofMillis(1234));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        SmartInitializingSingleton configurer =
                new NotificationProviderConfig().notificationMailTimeoutConfigurer(sender, properties);

        configurer.afterSingletonsInstantiated();

        assertEquals("1234", sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout"));
        assertEquals("1234", sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        assertEquals("1234", sender.getJavaMailProperties().getProperty("mail.smtp.writetimeout"));
    }

    private NotificationDispatchProperties properties(Duration timeout) {
        NotificationDispatchProperties properties = new NotificationDispatchProperties();
        properties.setProviderTimeout(timeout);
        return properties;
    }
}
