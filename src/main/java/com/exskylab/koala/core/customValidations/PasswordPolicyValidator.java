package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.constants.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return false;
        }

        return password.codePointCount(0, password.length()) >= PasswordPolicy.MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES;
    }
}
