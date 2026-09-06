package com.exskylab.koala.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class TimesheetPlausibilityFlagsConverter
        implements AttributeConverter<Set<TimesheetPlausibilityFlag>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TimesheetPlausibilityFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return "";
        }
        return flags.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<TimesheetPlausibilityFlag> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(TimesheetPlausibilityFlag.class);
        }
        return Arrays.stream(value.split(","))
                .map(TimesheetPlausibilityFlag::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TimesheetPlausibilityFlag.class)));
    }
}
