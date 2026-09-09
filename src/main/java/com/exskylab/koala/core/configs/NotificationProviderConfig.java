package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestTemplate;

import java.util.Properties;

@Configuration
public class NotificationProviderConfig {

    @Bean("notificationRestTemplate")
    public RestTemplate notificationRestTemplate(RestTemplateBuilder builder,
                                                  NotificationDispatchProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getProviderTimeout());
        factory.setReadTimeout(properties.getProviderTimeout());
        return builder.requestFactory(() -> factory).build();
    }

    @Bean
    public SmartInitializingSingleton notificationMailTimeoutConfigurer(
            JavaMailSender mailSender, NotificationDispatchProperties properties) {
        return () -> {
            if (mailSender instanceof JavaMailSenderImpl sender) {
                Properties mailProperties = sender.getJavaMailProperties();
                String timeout = Long.toString(properties.getProviderTimeout().toMillis());
                mailProperties.setProperty("mail.smtp.connectiontimeout", timeout);
                mailProperties.setProperty("mail.smtp.timeout", timeout);
                mailProperties.setProperty("mail.smtp.writetimeout", timeout);
            }
        };
    }
}
