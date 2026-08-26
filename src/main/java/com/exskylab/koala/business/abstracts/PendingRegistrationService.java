package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.auth.request.AuthStartRegistrationDto;
import com.exskylab.koala.entities.PendingRegistration;

import java.util.Optional;

import java.util.UUID;

public interface PendingRegistrationService {
    PendingRegistration getByEmail(String email);

    Optional<PendingRegistration> findByEmail(String email);

    void deleteById(UUID id);

    PendingRegistration createPendingRegistration(AuthStartRegistrationDto authStartRegistrationDto);

    PendingRegistration getByToken(UUID registrationToken);

    PendingRegistration getById(UUID pendingRegistrationId);
}
