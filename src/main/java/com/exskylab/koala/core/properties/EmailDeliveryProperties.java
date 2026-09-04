package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;
import java.util.Set;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "email.delivery")
public class EmailDeliveryProperties {

    public enum Mode { ALLOWLIST, UNRESTRICTED }

    @NotNull
    private Mode mode = Mode.ALLOWLIST;

    @NotNull
    private Set<@NotBlank @Email String> allowedRecipients = Set.of();

    public boolean allows(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        if (mode == Mode.UNRESTRICTED) {
            return true;
        }
        String normalized = recipient.strip().toLowerCase(Locale.ROOT);
        return allowedRecipients.stream()
                .map(address -> address.strip().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }
}
