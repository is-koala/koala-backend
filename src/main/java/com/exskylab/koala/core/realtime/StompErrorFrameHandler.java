package com.exskylab.koala.core.realtime;

import com.exskylab.koala.core.constants.RealtimeMessages;
import com.exskylab.koala.core.dtos.realtime.response.RealtimeEventDto;
import com.exskylab.koala.core.exceptions.KoalaException;
import com.exskylab.koala.core.helpers.MessageResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
public class StompErrorFrameHandler extends StompSubProtocolErrorHandler {

    private final MessageResolver messageResolver;
    private final ObjectMapper objectMapper;

    public StompErrorFrameHandler(MessageResolver messageResolver, ObjectMapper objectMapper) {
        this.messageResolver = messageResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        StompHeaderAccessor client = clientMessage == null ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);

        if (client != null && StompCommand.SUBSCRIBE.equals(client.getCommand())) {
            return subscriptionRefused(client, failure(ex));
        }

        return errorFrame(client, failure(ex));
    }

    private Message<byte[]> subscriptionRefused(StompHeaderAccessor client, KoalaException failure) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(client.getSessionId());
        accessor.setSubscriptionId(client.getSubscriptionId());
        accessor.setDestination(client.getDestination());
        accessor.setMessageId(UUID.randomUUID().toString());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        RealtimeEventDto event = RealtimeEventDto.subscriptionRefused(
                client.getDestination(), errorCode(failure), text(failure));

        return MessageBuilder.createMessage(objectMapper.writeValueAsBytes(event), accessor.getMessageHeaders());
    }

    private Message<byte[]> errorFrame(StompHeaderAccessor client, KoalaException failure) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(text(failure));
        accessor.setLeaveMutable(true);

        if (client != null && client.getReceipt() != null) {
            accessor.setReceiptId(client.getReceipt());
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private KoalaException failure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof KoalaException koala) {
                return koala;
            }
            current = current.getCause();
        }
        return null;
    }

    private String errorCode(KoalaException failure) {
        return failure == null ? "REALTIME_FRAME_REJECTED" : failure.getErrorCode();
    }

    private String text(KoalaException failure) {
        return failure == null
                ? messageResolver.resolve(RealtimeMessages.FRAME_REJECTED)
                : messageResolver.resolve(failure.getMessage());
    }
}
