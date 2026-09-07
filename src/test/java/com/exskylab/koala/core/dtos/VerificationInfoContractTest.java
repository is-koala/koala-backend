package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.userVerification.response.VerificationInfoDto;
import com.exskylab.koala.entities.EmailVerification;
import com.exskylab.koala.entities.PhoneVerification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VerificationInfoContractTest {

    private static final Instant EXPIRY = Instant.parse("2026-09-07T12:15:00Z");

    private EmailVerification emailVerification() {
        EmailVerification verification = new EmailVerification();
        verification.setId(UUID.randomUUID());
        verification.setNewEmail("yeni@koala.test");
        verification.setToken("123456");
        verification.setExpiresAt(EXPIRY);
        return verification;
    }

    private PhoneVerification phoneVerification() {
        PhoneVerification verification = new PhoneVerification();
        verification.setId(UUID.randomUUID());
        verification.setNewPhoneNumber("+905559998877");
        verification.setToken("123456");
        verification.setExpiresAt(EXPIRY);
        return verification;
    }

    @Test
    void anEmailVerificationCarriesTheMomentItsCodeDies() {
        VerificationInfoDto dto = new VerificationInfoDto(emailVerification());

        assertEquals("EMAIL", dto.getType());
        assertEquals(EXPIRY, dto.getExpiresAt());
    }

    @Test
    void aPhoneVerificationCarriesTheMomentItsCodeDies() {
        VerificationInfoDto dto = new VerificationInfoDto(phoneVerification());

        assertEquals("PHONE", dto.getType());
        assertEquals(EXPIRY, dto.getExpiresAt());
    }

    @Test
    void theExpiryIsAnInstantSoTheClientCanSubtractItFromNow() throws NoSuchFieldException {
        assertEquals(Instant.class, VerificationInfoDto.class.getDeclaredField("expiresAt").getType(),
                "Dogrulama kodunun omru bir andir, duvar saati degil (CLAUDE.md §2h).");
    }

    @Test
    void theExpiryTheManagersWriteIsTheOneTheClientReads() {
        Instant fifteenMinutesOut = Instant.now().plus(15, ChronoUnit.MINUTES);
        EmailVerification verification = emailVerification();
        verification.setExpiresAt(fifteenMinutesOut);

        assertEquals(fifteenMinutesOut, new VerificationInfoDto(verification).getExpiresAt());
    }

    @Test
    void noStringFieldReachesTheClientAsARawMessageKey() throws IllegalAccessException {
        for (Object dto : new Object[]{new VerificationInfoDto(emailVerification()),
                new VerificationInfoDto(phoneVerification())}) {
            for (Field field : dto.getClass().getDeclaredFields()) {
                if (field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                String value = (String) field.get(dto);
                assertFalse(value != null && value.startsWith("{"),
                        "VerificationInfoDto." + field.getName() + " ham i18n anahtari tasiyor: " + value);
            }
        }
    }

    @Test
    void theIdentityOfTheVerificationStillReachesTheClient() {
        VerificationInfoDto dto = new VerificationInfoDto(emailVerification());

        assertNotNull(dto.getId());
        assertEquals("yeni@koala.test", dto.getSentTo());
    }
}
