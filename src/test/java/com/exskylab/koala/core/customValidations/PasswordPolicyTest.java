package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.constants.PasswordPolicy;
import com.exskylab.koala.core.dtos.auth.request.AuthCompleteRegistrationDto;
import com.exskylab.koala.core.dtos.auth.request.AuthLoginDto;
import com.exskylab.koala.core.dtos.auth.request.ResetPasswordDto;
import com.exskylab.koala.core.dtos.user.request.ChangePasswordDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private static final int MIN_LENGTH = PasswordPolicy.MIN_LENGTH;
    private static final int MAX_BYTES = PasswordPolicy.MAX_BYTES;

    private static final String ONE_CHARACTER = "a";
    private static final String LONG_ENOUGH = "koala mavi ev kapi";

    private final Validator validator;

    PasswordPolicyTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private record Surface(String name, Function<String, Object> dto) {
    }

    private List<Surface> passwordSurfaces() {
        return List.of(
                new Surface("POST /auth/complete-registration", this::completeRegistration),
                new Surface("POST /auth/reset-password", this::resetPassword),
                new Surface("PUT /users/me/password", this::changePassword));
    }

    @Test
    void theRuleIsTwelveCharactersAndSeventyTwoBytesUntilSomeoneDecidesOtherwise() {
        assertEquals(12, MIN_LENGTH, "Parola kuralı değişmiş, gerekçesi CLAUDE.md §2ah2'de güncellenmeli");
        assertEquals(72, MAX_BYTES, "Üst sınır bcrypt'in 72 baytına bağlı, kodlayıcı değiştiyse §2ah2 güncellenmeli");
    }

    @Test
    void aOneCharacterPasswordIsRefusedByEveryPasswordSurface() {
        for (Surface surface : passwordSurfaces()) {
            assertTrue(violatedFields(surface.dto().apply(ONE_CHARACTER)).contains(passwordField(surface)),
                    surface.name() + " tek karakterlik parolayı kabul ediyor");
        }
    }

    @Test
    void aPasswordJustUnderTheMinimumIsRefusedByEveryPasswordSurface() {
        String tooShort = "a".repeat(MIN_LENGTH - 1);

        for (Surface surface : passwordSurfaces()) {
            assertTrue(violatedFields(surface.dto().apply(tooShort)).contains(passwordField(surface)),
                    surface.name() + " " + (MIN_LENGTH - 1) + " karakterlik parolayı kabul ediyor");
        }
    }

    @Test
    void aPasswordThatBcryptWouldSilentlyRefuseIsRefusedByEveryPasswordSurface() {
        String tooLong = "ş".repeat(MAX_BYTES);

        for (Surface surface : passwordSurfaces()) {
            assertTrue(violatedFields(surface.dto().apply(tooLong)).contains(passwordField(surface)),
                    surface.name() + " bcrypt'in 72 bayt sınırını aşan parolayı kabul ediyor");
        }
    }

    @Test
    void aLongPassphraseWithoutDigitsOrCapitalsIsAcceptedByEveryPasswordSurface() {
        for (Surface surface : passwordSurfaces()) {
            assertFalse(violatedFields(surface.dto().apply(LONG_ENOUGH)).contains(passwordField(surface)),
                    surface.name() + " uzun bir parolayı karmaşıklık kuralı yüzünden reddediyor");
        }
    }

    @Test
    void theLoginSurfaceKeepsAcceptingWhateverTheUserAlreadyHas() {
        AuthLoginDto login = new AuthLoginDto("ada@example.com", ONE_CHARACTER);

        assertFalse(violatedFields(login).contains("password"),
                "Giriş ucu var olan parolaya politika uyguluyor, eski kullanıcılar dışarıda kalır");
    }

    @Test
    void theCatalogsStillDescribeTheRuleTheValidatorEnforces() throws IOException {
        for (String file : new String[]{"messages_tr.properties", "messages_en.properties"}) {
            String message = load(file).getProperty("user.password.policy");

            assertTrue(message != null, file + " icinde user.password.policy yok");
            assertTrue(message.contains(String.valueOf(MIN_LENGTH)),
                    file + " kurala uymuyor, en az " + MIN_LENGTH + " yazmiyor: " + message);
            assertTrue(message.contains(String.valueOf(MAX_BYTES)),
                    file + " kurala uymuyor, en fazla " + MAX_BYTES + " yazmiyor: " + message);
        }
    }

    private String passwordField(Surface surface) {
        return surface.name().contains("complete-registration") ? "password" : "newPassword";
    }

    private Set<String> violatedFields(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private Object completeRegistration(String password) {
        AuthCompleteRegistrationDto dto = new AuthCompleteRegistrationDto();
        dto.setConsents(List.of());
        dto.setSetPasswordToken("token");
        dto.setPassword(password);
        return dto;
    }

    private Object resetPassword(String password) {
        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setResetToken("token");
        dto.setNewPassword(password);
        return dto;
    }

    private Object changePassword(String password) {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("eski parola degeri");
        dto.setNewPassword(password);
        dto.setConfirmNewPassword(password);
        return dto;
    }

    private Properties load(String name) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, name + " bulunamadi");
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }
}
