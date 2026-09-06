package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.constants.UserMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TurkishIbanValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTurkishIban {

    String message() default UserMessages.INVALID_IBAN;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
