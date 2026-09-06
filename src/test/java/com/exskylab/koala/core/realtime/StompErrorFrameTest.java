package com.exskylab.koala.core.realtime;

import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.helpers.MessageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StompErrorFrameTest {

    private static final String REFUSAL_TEXT = "Bu kanala abone olamazsınız.";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.SUPERSECRETPAYLOAD.c2lnbmF0dXJl";
    private static final String REJECTED_TEXT = "Bu istek gerçek zamanlı kanalda reddedildi.";

    private final String destination = "/topic/conversations/" + UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private StompErrorFrameHandler handler;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("messaging.subscription.not.allowed",
                LocaleContextHolder.getLocale(), REFUSAL_TEXT);
        messages.addMessage("realtime.frame.rejected",
                LocaleContextHolder.getLocale(), REJECTED_TEXT);

        handler = new StompErrorFrameHandler(new MessageResolver(messages), objectMapper);
    }

    @Test
    void aRefusedSubscriptionIsAnsweredOnThatSubscriptionInsteadOfClosingTheSocket() {
        Message<byte[]> reply = handler.handleClientMessageProcessingError(subscribe(), refusal());

        assertNotNull(reply);
        StompHeaderAccessor accessor = accessorOf(reply);

        assertNotEquals(StompCommand.ERROR, accessor.getCommand());
        assertEquals(StompCommand.MESSAGE, accessor.getCommand());
        assertEquals("stomp-1", accessor.getSessionId());
        assertEquals("sub-7", accessor.getSubscriptionId());
        assertEquals(destination, accessor.getDestination());

        JsonNode event = objectMapper.readTree(reply.getPayload());
        assertEquals("ERROR", event.get("type").asString());
        assertEquals(destination, event.get("error").get("destination").asString());
        assertEquals("MESSAGING_SUBSCRIPTION_NOT_ALLOWED", event.get("error").get("errorCode").asString());
        assertEquals(REFUSAL_TEXT, event.get("error").get("message").asString());
    }

    @Test
    void aFailedConnectIsAnsweredWithCatalogTextAndNeverWithTheBearerToken() {
        Message<byte[]> reply = handler.handleClientMessageProcessingError(connect(), badToken());

        assertNotNull(reply);
        StompHeaderAccessor accessor = accessorOf(reply);

        assertEquals(StompCommand.ERROR, accessor.getCommand());
        assertEquals(REJECTED_TEXT, accessor.getMessage());

        String frame = accessor.getMessageHeaders() + new String(reply.getPayload(), StandardCharsets.UTF_8);
        assertFalse(frame.contains(TOKEN));
        assertFalse(frame.contains("clientInboundChannel"));
    }

    private Message<byte[]> connect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("stomp-2");
        accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Throwable badToken() {
        return new MessageDeliveryException(connect(), "Failed to send message to clientInboundChannel",
                new IllegalArgumentException("JWT strings must contain exactly 2 period characters."));
    }

    private Message<byte[]> subscribe() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("stomp-1");
        accessor.setSubscriptionId("sub-7");
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Throwable refusal() {
        return new MessageDeliveryException(subscribe(), "Failed to send message to clientInboundChannel",
                new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED));
    }

    private StompHeaderAccessor accessorOf(Message<byte[]> message) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertNotNull(accessor);
        return accessor;
    }
}
