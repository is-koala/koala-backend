package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PendingRegistrationService;
import com.exskylab.koala.core.constants.PendingRegistrationMessages;
import com.exskylab.koala.core.dtos.auth.request.AuthStartRegistrationDto;
import com.exskylab.koala.core.exceptions.PendingRegistrationNotFoundException;
import com.exskylab.koala.dataAccess.PendingRegistrationDao;
import com.exskylab.koala.entities.PendingRegistration;

import java.util.Optional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PendingRegistrationManager implements PendingRegistrationService {

    private final PendingRegistrationDao pendingRegistrationDao;

    public PendingRegistrationManager(PendingRegistrationDao pendingRegistrationDao) {
        this.pendingRegistrationDao = pendingRegistrationDao;
    }

    @Override
    public PendingRegistration getByEmail(String email) {
        return pendingRegistrationDao.findByEmail(email)
                .orElseThrow(() -> new PendingRegistrationNotFoundException(PendingRegistrationMessages.PENDING_REGISTRATION_NOT_FOUND_BY_EMAIL));
    }

    @Override
    public void deleteById(UUID id) {
        if (!pendingRegistrationDao.existsById(id)) {
            throw new PendingRegistrationNotFoundException(PendingRegistrationMessages.PENDING_REGISTRATION_NOT_FOUND_BY_ID);
        }
        pendingRegistrationDao.deleteById(id);
    }

    @Override
    public PendingRegistration createPendingRegistration(AuthStartRegistrationDto authStartRegistrationDto) {
        var pendingRegistration = new PendingRegistration();
        pendingRegistration.setEmail(authStartRegistrationDto.getEmail());
        pendingRegistration.setFirstName(authStartRegistrationDto.getFirstName());
        pendingRegistration.setLastName(authStartRegistrationDto.getLastName());
        pendingRegistration.setEmployer(authStartRegistrationDto.isEmployer());
        pendingRegistration.setExpiresAt(LocalDateTime.now().plusHours(1));
        pendingRegistration.setToken(UUID.randomUUID());
        return pendingRegistrationDao.save(pendingRegistration);
    }

    @Override
    public PendingRegistration getByToken(UUID registrationToken) {
        return pendingRegistrationDao.findByToken(registrationToken)
                .orElseThrow(() -> new PendingRegistrationNotFoundException(PendingRegistrationMessages.PENDING_REGISTRATION_NOT_FOUND_BY_TOKEN));
    }

    @Override
    public PendingRegistration getById(UUID pendingRegistrationId) {
        return pendingRegistrationDao.findById(pendingRegistrationId)
                .orElseThrow(() -> new PendingRegistrationNotFoundException(PendingRegistrationMessages.PENDING_REGISTRATION_NOT_FOUND_BY_ID));
    }



    @Override
    public Optional<PendingRegistration> findByEmail(String email) {
        return pendingRegistrationDao.findByEmail(email);
    }
}
