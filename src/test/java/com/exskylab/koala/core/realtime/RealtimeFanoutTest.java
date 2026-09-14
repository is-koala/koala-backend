package com.exskylab.koala.core.realtime;

import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.core.dtos.realtime.response.RealtimeEventDto;
import com.exskylab.koala.core.properties.RealtimeProperties;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.RealtimeEventType;
import com.exskylab.koala.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RealtimeFanoutTest {

    private SimpMessagingTemplate messagingTemplate;
    private RealtimeProperties properties;
    private RealtimePublisher publisher;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        properties = new RealtimeProperties();
        publisher = new RealtimePublisher(messagingTemplate, properties);
    }

    @Test
    void aSentMessageReachesTheConversationTopic() {
        publisher.messageSent(conversationId, message(workerId), List.of(employerId, workerId));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId), any(RealtimeEventDto.class));
    }

    @Test
    void aSentMessageAlsoReachesBothPersonalQueues() {
        publisher.messageSent(conversationId, message(workerId), List.of(employerId, workerId));

        verify(messagingTemplate).convertAndSendToUser(
                eq(employerId.toString()), eq("/queue/messages"), any(RealtimeEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(workerId.toString()), eq("/queue/messages"), any(RealtimeEventDto.class));
    }

    @Test
    void thePersonalCopyCarriesTheSameEventAsTheTopic() {
        publisher.messageSent(conversationId, message(workerId), List.of(employerId));

        ArgumentCaptor<RealtimeEventDto> captor = ArgumentCaptor.forClass(RealtimeEventDto.class);
        verify(messagingTemplate).convertAndSendToUser(any(), any(), captor.capture());

        assertEquals(RealtimeEventType.MESSAGE, captor.getValue().type());
        assertEquals(conversationId, captor.getValue().conversationId());
        assertNotNull(captor.getValue().message());
    }

    @Test
    void aReadEventNamesTheReaderAndCarriesTheirNewStamp() {
        Instant stamp = Instant.now();

        publisher.conversationRead(conversationId, workerId, stamp, List.of(employerId, workerId));

        ArgumentCaptor<RealtimeEventDto> captor = ArgumentCaptor.forClass(RealtimeEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId), captor.capture());
        assertEquals(RealtimeEventType.READ, captor.getValue().type());
        assertEquals(workerId, captor.getValue().userId());
        assertEquals(stamp, captor.getValue().lastReadAt());
        verify(messagingTemplate).convertAndSendToUser(
                eq(employerId.toString()), eq("/queue/messages"), any(RealtimeEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(workerId.toString()), eq("/queue/messages"), any(RealtimeEventDto.class));
    }

    @Test
    void presenceGoesToThePersonsOwnTopicNotAGlobalOne() {
        publisher.presenceChanged(workerId, true);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/presence/" + workerId), any(RealtimeEventDto.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/presence"), any(Object.class));
    }

    @Test
    void aBrokerFailureNeverBreaksTheCallingFlow() {
        doThrow(new IllegalStateException("broker down"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any(Object.class));

        assertDoesNotThrow(() ->
                publisher.messageSent(conversationId, message(workerId), List.of(employerId, workerId)));

        verify(messagingTemplate, times(2)).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void nothingIsPublishedWhenTheRealtimeLayerIsOff() {
        properties.setEnabled(false);

        publisher.messageSent(conversationId, message(workerId), List.of(employerId, workerId));
        publisher.presenceChanged(workerId, true);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void theUserDestinationIsKeyedByIdNotEmail() {
        UserPrincipal principal = new UserPrincipal(workerId, "isci@koala.test",
                Set.of(Role.ROLE_USER), UUID.randomUUID());

        assertEquals(workerId.toString(), new RealtimeAuthentication(principal).getName());
    }

    private static MessageDto message(UUID senderId) {
        return new MessageDto(UUID.randomUUID(), senderId, "Ayşe Yılmaz", null, "merhaba", Instant.now(), null);
    }
}
