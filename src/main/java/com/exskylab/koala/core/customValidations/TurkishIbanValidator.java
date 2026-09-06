package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.helpers.IbanValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TurkishIbanValidator implements ConstraintValidator<ValidTurkishIban, String> {

    @Override
    public boolean isValid(String iban, ConstraintValidatorContext context) {
        return iban == null || iban.isBlank() || IbanValidator.isValid(iban);
    }
}
