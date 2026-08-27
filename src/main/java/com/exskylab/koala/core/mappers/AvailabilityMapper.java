package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.availability.response.AvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.availability.response.AvailabilitySlotDto;
import com.exskylab.koala.entities.AvailabilityException;
import com.exskylab.koala.entities.AvailabilitySlot;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AvailabilityMapper {

    AvailabilitySlotDto toSlotDto(AvailabilitySlot slot);

    List<AvailabilitySlotDto> toSlotDtoList(List<AvailabilitySlot> slots);

    AvailabilityExceptionDto toExceptionDto(AvailabilityException exception);

    List<AvailabilityExceptionDto> toExceptionDtoList(List<AvailabilityException> exceptions);
}
