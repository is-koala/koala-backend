package com.exskylab.koala.core.customValidations;

import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimesheetEventValidator implements ConstraintValidator<ValidTimesheetEvent, CheckPointDto> {

    @Override
    public boolean isValid(CheckPointDto request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        return (request.getClientEventId() == null) == (request.getCapturedAt() == null);
    }
}
