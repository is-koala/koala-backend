package com.exskylab.koala.core.consumers;

import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

public class NotificationDeliveryBusyRecoverer implements MessageRecoverer {

    private final ConfirmedRabbitPublisher publisher;

    public NotificationDeliveryBusyRecoverer(ConfirmedRabbitPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        if (!hasBusyCause(cause)) {
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Notification delivery retry failed", cause);
        }
        publisher.publish(RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY, message);
    }

    private boolean hasBusyCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof NotificationDeliveryBusyException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
