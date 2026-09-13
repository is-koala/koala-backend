package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.PaymentReturnProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Component
public class PaymentReturnUrlBuilder {

    private final PaymentReturnProperties properties;

    public PaymentReturnUrlBuilder(PaymentReturnProperties properties) {
        this.properties = properties;
    }

    public String resultUrl(boolean succeeded, UUID assignmentId) {
        return resultPage(succeeded)
                .queryParam("assignmentId", assignmentId)
                .build()
                .encode()
                .toUriString();
    }

    public String unknownAttemptResultUrl() {
        return resultPage(false)
                .build()
                .encode()
                .toUriString();
    }

    private UriComponentsBuilder resultPage(boolean succeeded) {
        UriComponentsBuilder builder = switch (properties.returnTarget()) {
            case PaymentReturnProperties.WebOrigin webOrigin -> UriComponentsBuilder
                    .fromUri(webOrigin.value())
                    .pathSegment("payment", "result");
            case PaymentReturnProperties.AppScheme appScheme -> UriComponentsBuilder
                    .newInstance()
                    .scheme(appScheme.value())
                    .host("payment-result");
        };

        return builder.queryParam("status", succeeded ? "success" : "failed");
    }
}
