package com.exskylab.koala.core.utilities.iys;

public interface IysProviderClient {

    void registerConsent(IysConsentRequest request);

    IysPermissionResult lookupConsent(String recipient, com.exskylab.koala.entities.NotificationChannel channel);
}
