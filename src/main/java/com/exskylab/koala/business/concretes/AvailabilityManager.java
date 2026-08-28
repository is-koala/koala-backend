package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.AvailabilityService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.AvailabilityMessages;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityExceptionDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.AvailabilityExceptionDao;
import com.exskylab.koala.dataAccess.AvailabilitySlotDao;
import com.exskylab.koala.entities.AvailabilityException;
import com.exskylab.koala.entities.AvailabilitySlot;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AvailabilityManager implements AvailabilityService {

    private final AvailabilitySlotDao slotDao;
    private final AvailabilityExceptionDao exceptionDao;
    private final SecurityService securityService;

    public AvailabilityManager(AvailabilitySlotDao slotDao, AvailabilityExceptionDao exceptionDao,
                               SecurityService securityService) {
        this.slotDao = slotDao;
        this.exceptionDao = exceptionDao;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> getSlotsOf(UUID userId) {
        return slotDao.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilityException> getUpcomingExceptionsOf(UUID userId) {
        return exceptionDao.findAllByUserIdAndDateGreaterThanEqualOrderByDateAsc(userId, LocalDate.now());
    }

    @Override
    @Transactional
    public List<AvailabilitySlot> replaceMySlots(SaveAvailabilityDto request) {
        assertSlotsAreSane(request.getSlots());

        User currentUser = securityService.getAuthenticatedUser();
        slotDao.deleteAll(slotDao.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(currentUser.getId()));

        List<AvailabilitySlot> saved = new ArrayList<>();
        for (SaveAvailabilityDto.Slot requested : request.getSlots()) {
            AvailabilitySlot slot = new AvailabilitySlot();
            slot.setUser(currentUser);
            slot.setDayOfWeek(requested.getDayOfWeek());
            slot.setStartTime(requested.getStartTime());
            slot.setEndTime(requested.getEndTime());
            saved.add(slotDao.save(slot));
        }

        log.info("Availability replaced for user {} with {} slots", currentUser.getId(), saved.size());
        return saved;
    }

    @Override
    @Transactional
    public AvailabilityException saveMyException(SaveAvailabilityExceptionDto request) {
        User currentUser = securityService.getAuthenticatedUser();

        AvailabilityException exception = exceptionDao
                .findByUserIdAndDate(currentUser.getId(), request.getDate())
                .orElseGet(() -> {
                    AvailabilityException created = new AvailabilityException();
                    created.setUser(currentUser);
                    created.setDate(request.getDate());
                    return created;
                });

        exception.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        exception.setNote(request.getNote());

        AvailabilityException saved = exceptionDao.save(exception);
        log.info("Availability exception on {} saved for user {}", saved.getDate(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteMyException(UUID exceptionId) {
        AvailabilityException exception = exceptionDao.findWithUserById(exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException(AvailabilityMessages.NOT_FOUND));

        if (!exception.getUser().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(AvailabilityMessages.NOT_FOUND);
        }

        exceptionDao.delete(exception);
        log.info("Availability exception {} deleted", exceptionId);
    }

    private void assertSlotsAreSane(List<SaveAvailabilityDto.Slot> slots) {
        List<SaveAvailabilityDto.Slot> ordered = slots.stream()
                .sorted(Comparator.comparing(SaveAvailabilityDto.Slot::getDayOfWeek)
                        .thenComparing(SaveAvailabilityDto.Slot::getStartTime))
                .toList();

        for (int index = 1; index < ordered.size(); index++) {
            SaveAvailabilityDto.Slot previous = ordered.get(index - 1);
            SaveAvailabilityDto.Slot current = ordered.get(index);

            if (previous.getDayOfWeek() == current.getDayOfWeek()
                    && current.getStartTime().isBefore(previous.getEndTime())) {
                throw new ValidationException(AvailabilityMessages.OVERLAPPING_SLOTS);
            }
        }
    }
}
