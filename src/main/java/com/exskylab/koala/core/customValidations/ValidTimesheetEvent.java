package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.constants.TimesheetMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TimesheetEventValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimesheetEvent {

    String message() default TimesheetMessages.CLIENT_EVENT_PAIR;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
