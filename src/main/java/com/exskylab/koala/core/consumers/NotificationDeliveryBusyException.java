package com.exskylab.koala.core.consumers;

public class NotificationDeliveryBusyException extends IllegalStateException {

    public NotificationDeliveryBusyException() {
        super("Notification delivery claim is busy");
    }
}
