package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.auth.request.AuthStartRegistrationDto;
import com.exskylab.koala.core.dtos.auth.response.AuthSetPasswordDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployerFlagJsonContractTest {

    private final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Test
    void anEmployerRegistrationIsNotSilentlyDowngradedToAWorker() {
        String body = """
                {"firstName":"Ada","lastName":"Yilmaz","email":"ada@example.com","isEmployer":true}
                """;

        AuthStartRegistrationDto dto = mapper.readValue(body, AuthStartRegistrationDto.class);

        assertTrue(Boolean.TRUE.equals(dto.getIsEmployer()),
                "Istemci isEmployer:true gonderdi; bu bayrak sessizce false'a dusmemeli.");
    }

    @Test
    void theRegistrationResponseUsesTheSameKeyTheClientReads() {
        AuthSetPasswordDto dto = new AuthSetPasswordDto("token", true);

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"isEmployer\""),
                "Yanit anahtari istemcinin okudugu adla ayni olmali: " + json);
    }

    @Test
    void theProfileResponseUsesTheSameKeyEverywhereElseDoes() {
        UserMeResponseDto dto = new UserMeResponseDto();
        dto.setEmployer(true);

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"isEmployer\""),
                "Istek ve yanit ayni anahtari kullanmali: " + json);
    }

    @Test
    void aWorkerRegistrationStaysAWorker() {
        String body = """
                {"firstName":"Ada","lastName":"Yilmaz","email":"ada@example.com","isEmployer":false}
                """;

        assertFalse(Boolean.TRUE.equals(mapper.readValue(body, AuthStartRegistrationDto.class).getIsEmployer()));
    }

    @Test
    void omittingTheFlagIsNoLongerASilentWorkerRegistration() {
        String body = """
                {"firstName":"Ada","lastName":"Yilmaz","email":"ada@example.com"}
                """;

        AuthStartRegistrationDto dto = mapper.readValue(body, AuthStartRegistrationDto.class);

        assertNull(dto.getIsEmployer(),
                "Alan eksikse null kalmali; @NotNull dogrulamasi 400 uretsin diye. "
                        + "Sessizce false'a dusmek isvereni calisan olarak kaydediyordu.");
    }
}
