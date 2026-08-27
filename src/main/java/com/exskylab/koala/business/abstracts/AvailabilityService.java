package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityExceptionDto;
import com.exskylab.koala.entities.AvailabilityException;
import com.exskylab.koala.entities.AvailabilitySlot;

import java.util.List;
import java.util.UUID;

public interface AvailabilityService {

    List<AvailabilitySlot> getSlotsOf(UUID userId);

    List<AvailabilityException> getUpcomingExceptionsOf(UUID userId);

    List<AvailabilitySlot> replaceMySlots(SaveAvailabilityDto request);

    AvailabilityException saveMyException(SaveAvailabilityExceptionDto request);

    void deleteMyException(UUID exceptionId);
}
