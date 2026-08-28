package com.exskylab.koala.core.realtime;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.core.dtos.realtime.response.RealtimeEventDto;
import com.exskylab.koala.core.properties.RealtimeProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class RealtimePublisher {

    private static final String CONVERSATION_TOPIC = "/topic/conversations/";
    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeProperties properties;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate, RealtimeProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
    }

    public void messageSent(UUID conversationId, MessageDto message) {
        publish(CONVERSATION_TOPIC + conversationId, RealtimeEventDto.message(conversationId, message));
    }

    public void typing(UUID conversationId, UUID userId) {
        publish(CONVERSATION_TOPIC + conversationId, RealtimeEventDto.typing(conversationId, userId));
    }

    public void conversationRead(UUID conversationId, UUID readerId) {
        publish(CONVERSATION_TOPIC + conversationId, RealtimeEventDto.read(conversationId, readerId));
    }

    public void presenceChanged(UUID userId, boolean online) {
        publish(PRESENCE_TOPIC, RealtimeEventDto.presence(userId, online));
    }

    private void publish(String destination, RealtimeEventDto event) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.warn("Realtime publish to {} failed: {}", destination, e.getMessage());
        }
    }
}
