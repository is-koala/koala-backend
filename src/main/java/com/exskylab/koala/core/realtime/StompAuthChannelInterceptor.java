package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final Logger logger = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;
    private final SessionService sessionService;

    public StompAuthChannelInterceptor(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = BearerTokenExtractor.extractHeader(accessor.getFirstNativeHeader("Authorization"));

        UUID sessionId = jwtService.extractSessionId(token);
        Session session = sessionService.findActiveSessionById(sessionId);
        UserPrincipal principal = UserPrincipal.from(session.getDevice().getUser(), sessionId);

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));

        logger.debug("STOMP connection authenticated for user {}", principal.id());
        return message;
    }
}
