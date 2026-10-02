package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.entities.BoardingKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SeatBoardingDto {

    private String token;

    private Instant capturedAt;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    private UUID clientEventId;

    private BoardingKind kind;

    @AssertTrue(message = TransportMessages.CAPTURED_AT_NOT_NULL)
    public boolean isCapturedAtPresentWhenTokenPresent() {
        return token == null || token.isBlank() || capturedAt != null;
    }

    @AssertTrue(message = TransportMessages.BOARDING_KIND_RULE)
    public boolean isKindConsistentWithToken() {
        if (token != null && !token.isBlank()) {
            return kind == null || kind == BoardingKind.BOARDED;
        }
        return kind == null || kind == BoardingKind.BOARDED
                || kind == BoardingKind.NO_SHOW || kind == BoardingKind.ALIGHTED;
    }
}
