package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "email.support")
public class SupportInboxProperties {

    /**
     * Formun alıcısı. SMTP kullanıcısı olmak zorunda değil; gönderen
     * `MAIL_USERNAME`, kutu `info@iskoala.com` olabilir.
     */
    @NotBlank
    @Email
    private String mailbox = "info@iskoala.com";
}
