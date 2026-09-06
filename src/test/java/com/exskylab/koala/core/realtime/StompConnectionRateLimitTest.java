package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.ConversationDao;
import com.exskylab.koala.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class StompConnectionRateLimitTest {

    private final UUID userId = UUID.randomUUID();

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRealtimeSubscribe(new RateLimitProperties.Bucket(2, Duration.ofMinutes(1)));
        properties.setRealtimeMessage(new RateLimitProperties.Bucket(2, Duration.ofMinutes(1)));

        interceptor = new StompAuthChannelInterceptor(mock(JwtService.class), mock(SessionService.class),
                mock(ConversationDao.class), new RateLimiter(properties), properties);
    }

    @Test
    void aConnectionThatFloodsSubscriptionsIsRefusedOnceItsBudgetIsSpent() {
        assertDoesNotThrow(() -> interceptor.preSend(ownPresence("stomp-1"), null));
        assertDoesNotThrow(() -> interceptor.preSend(ownPresence("stomp-1"), null));

        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preSend(ownPresence("stomp-1"), null));
    }

    @Test
    void aFloodedMessageFrameIsDroppedRatherThanClosingTheConnection() {
        assertNotNull(interceptor.preSend(typing("stomp-1"), null));
        assertNotNull(interceptor.preSend(typing("stomp-1"), null));

        assertNull(interceptor.preSend(typing("stomp-1"), null));
    }

    @Test
    void oneConnectionCannotSpendAnotherConnectionsBudget() {
        interceptor.preSend(ownPresence("stomp-1"), null);
        interceptor.preSend(ownPresence("stomp-1"), null);

        assertDoesNotThrow(() -> interceptor.preSend(ownPresence("stomp-2"), null));
        assertNotNull(interceptor.preSend(typing("stomp-1"), null));
    }

    private Message<?> typing(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/conversations/" + UUID.randomUUID() + "/typing");
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> ownPresence(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/presence/" + userId);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        accessor.setUser(new RealtimeAuthentication(
                new UserPrincipal(userId, "a@b.test", Set.of(Role.ROLE_USER), UUID.randomUUID())));

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
