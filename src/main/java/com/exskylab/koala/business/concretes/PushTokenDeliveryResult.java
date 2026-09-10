package com.exskylab.koala.business.concretes;

record PushTokenDeliveryResult<T>(boolean lockAcquired, boolean currentOwner, T result) {

    static <T> PushTokenDeliveryResult<T> contended() {
        return new PushTokenDeliveryResult<>(false, false, null);
    }

    static <T> PushTokenDeliveryResult<T> notCurrent() {
        return new PushTokenDeliveryResult<>(true, false, null);
    }

    static <T> PushTokenDeliveryResult<T> current(T result) {
        return new PushTokenDeliveryResult<>(true, true, result);
    }
}
