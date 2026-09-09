package com.exskylab.koala.core.consumers;

import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryBusyRetryTest {

    @Test
    void aBusyDeliveryIsRetriedWithABoundedDelayUntilTheLeaseCanBeClaimed() throws Throwable {
        NotificationDispatchProperties properties = properties(2, Duration.ofMillis(1));
        NotificationDeliveryBusyRecoverer recoverer = mock(NotificationDeliveryBusyRecoverer.class);
        StatelessRetryOperationsInterceptor interceptor =
                new RabbitMQConfig().notificationDeliveryBusyRetryInterceptor(properties, recoverer);
        MethodInvocation invocation = invocation();
        when(invocation.proceed())
                .thenThrow(new NotificationDeliveryBusyException())
                .thenThrow(new NotificationDeliveryBusyException())
                .thenReturn(null);

        assertDoesNotThrow(() -> interceptor.invoke(invocation));

        verify(invocation, times(3)).proceed();
        verify(recoverer, never()).recover(any(), any());
    }

    @Test
    void anExhaustedBusyDeliveryIsParkedInsteadOfHotRequeued() throws Throwable {
        NotificationDispatchProperties properties = properties(2, Duration.ofMillis(1));
        NotificationDeliveryBusyRecoverer recoverer = mock(NotificationDeliveryBusyRecoverer.class);
        StatelessRetryOperationsInterceptor interceptor =
                new RabbitMQConfig().notificationDeliveryBusyRetryInterceptor(properties, recoverer);
        Message message = new Message(new byte[0]);
        MethodInvocation invocation = invocation();
        when(invocation.getArguments()).thenReturn(new Object[]{null, message});
        when(invocation.proceed()).thenThrow(new NotificationDeliveryBusyException());

        assertDoesNotThrow(() -> interceptor.invoke(invocation));

        verify(invocation, times(3)).proceed();
        verify(recoverer).recover(any(), any());
    }

    @Test
    void anExhaustedBusyDeliveryUsesTheConfirmedPublisherAndPreservesTheSourceMessage() {
        ConfirmedRabbitPublisher publisher = mock(ConfirmedRabbitPublisher.class);
        NotificationDeliveryBusyRecoverer recoverer = new NotificationDeliveryBusyRecoverer(publisher);
        Message message = new Message(new byte[]{1, 2, 3});

        recoverer.recover(message, new NotificationDeliveryBusyException());

        verify(publisher).publish(RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY, message);
    }

    @Test
    void aParkingConfirmationFailureEscapesSoTheSourceDeliveryIsNotAcknowledged() {
        ConfirmedRabbitPublisher publisher = mock(ConfirmedRabbitPublisher.class);
        NotificationDeliveryBusyRecoverer recoverer = new NotificationDeliveryBusyRecoverer(publisher);
        Message message = new Message(new byte[]{1, 2, 3});
        IllegalStateException confirmationFailure = new IllegalStateException("broker nack");
        doThrow(confirmationFailure).when(publisher).publish(
                RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY, message);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> recoverer.recover(message, new NotificationDeliveryBusyException()));

        assertSame(confirmationFailure, thrown);
    }

    @Test
    void theBusyParkingRouteIsDurableAndBoundToItsRoutingKey() {
        RabbitMQConfig config = new RabbitMQConfig();

        assertTrue(config.notificationDeliveryBusyParkingExchange().isDurable());
        assertTrue(config.notificationDeliveryBusyParkingQueue().isDurable());
        assertEquals(RabbitMQConfig.NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY,
                config.notificationDeliveryBusyParkingBinding().getRoutingKey());
    }

    private MethodInvocation invocation() {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{null, new Message(new byte[0])});
        return invocation;
    }

    private NotificationDispatchProperties properties(int maxRetries, Duration interval) {
        NotificationDispatchProperties properties = new NotificationDispatchProperties();
        properties.setDeliveryBusyMaxRetries(maxRetries);
        properties.setDeliveryBusyRetryInterval(interval);
        return properties;
    }
}
