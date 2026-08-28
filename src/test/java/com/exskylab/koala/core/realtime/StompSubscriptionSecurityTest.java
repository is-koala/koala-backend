package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.ConversationDao;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StompSubscriptionSecurityTest {

    private ConversationDao conversationDao;
    private StompAuthChannelInterceptor interceptor;

    private final User participant = user();
    private final User outsider = user();
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversationDao = mock(ConversationDao.class);
        interceptor = new StompAuthChannelInterceptor(
                mock(JwtService.class), mock(SessionService.class), conversationDao);

        User employer = user();
        conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setEmployer(employer);
        conversation.setWorker(participant);
        conversation.addParticipant(employer);
        conversation.addParticipant(participant);

        when(conversationDao.findById(conversation.getId())).thenReturn(Optional.of(conversation));
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private Message<?> subscribe(String destination, User as) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("stomp-1");
        accessor.setLeaveMutable(true);

        if (as != null) {
            UserPrincipal principal = new UserPrincipal(as.getId(), "a@b.test",
                    Set.of(Role.ROLE_USER), UUID.randomUUID());
            accessor.setUser(new RealtimeAuthentication(principal));
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void aParticipantMaySubscribeToTheirOwnConversation() {
        assertDoesNotThrow(() ->
                interceptor.preSend(subscribe("/topic/conversations/" + conversation.getId(), participant), null));
    }

    @Test
    void anOutsiderCannotSubscribeToSomeoneElsesConversation() {
        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/conversations/" + conversation.getId(), outsider), null));
    }

    @Test
    void subscribingToAConversationThatDoesNotExistIsRefused() {
        UUID ghost = UUID.randomUUID();
        when(conversationDao.findById(ghost)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/conversations/" + ghost, participant), null));
    }

    @Test
    void anUnknownDestinationIsRefusedRatherThanSilentlyAllowed() {
        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/admin/secrets", participant), null));
        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/conversations/not-a-uuid", participant), null));
    }

    @Test
    void anUnauthenticatedSubscriptionIsRefused() {
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preSend(subscribe("/topic/conversations/" + conversation.getId(), null), null));
    }

    @Test
    void theOldGlobalPresenceTopicIsGone() {
        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/presence", participant), null));
    }

    @Test
    void anyoneMayWatchTheirOwnPresence() {
        assertDoesNotThrow(() ->
                interceptor.preSend(subscribe("/topic/presence/" + participant.getId(), participant), null));
    }

    @Test
    void presenceOfSomeoneYouTalkToMayBeWatched() {
        when(conversationDao.existsBetweenParticipants(participant.getId(), outsider.getId())).thenReturn(true);

        assertDoesNotThrow(() ->
                interceptor.preSend(subscribe("/topic/presence/" + outsider.getId(), participant), null));
    }

    @Test
    void presenceOfAStrangerCannotBeWatched() {
        when(conversationDao.existsBetweenParticipants(any(), any())).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                interceptor.preSend(subscribe("/topic/presence/" + outsider.getId(), participant), null));
    }

    @Test
    void aPersonalQueueIsLeftToTheBrokersOwnUserScoping() {
        assertDoesNotThrow(() -> interceptor.preSend(subscribe("/user/queue/replies", participant), null));
    }

    @Test
    void framesOtherThanConnectAndSubscribePassThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/conversations/x/typing");
        accessor.setLeaveMutable(true);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertSame(message, interceptor.preSend(message, null));
        verify(conversationDao, never()).findById(any());
        verify(conversationDao, never()).existsBetweenParticipants(any(), any());
    }
}
