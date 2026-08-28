package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.ConversationDao;
import com.exskylab.koala.entities.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");

    private static final Set<String> OPEN_TOPICS = Set.of("/topic/presence");

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final ConversationDao conversationDao;

    public StompAuthChannelInterceptor(JwtService jwtService, SessionService sessionService,
                                       ConversationDao conversationDao) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.conversationDao = conversationDao;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authoriseSubscription(accessor);
            default -> {
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = BearerTokenExtractor.extractHeader(accessor.getFirstNativeHeader("Authorization"));

        UUID sessionId = jwtService.extractSessionId(token);
        Session session = sessionService.findActiveSessionById(sessionId);
        UserPrincipal principal = UserPrincipal.from(session.getDevice().getUser(), sessionId);

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));

        log.debug("Realtime connection authenticated for user {}", principal.id());
    }

    private void authoriseSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
        }

        if (OPEN_TOPICS.contains(destination) || destination.startsWith("/user/")) {
            return;
        }

        Matcher conversation = CONVERSATION_TOPIC.matcher(destination);
        if (!conversation.matches()) {
            log.warn("Realtime subscription to an unknown destination was refused: {}", destination);
            throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
        }

        UUID userId = currentUserId(accessor.getUser());
        UUID conversationId = UUID.fromString(conversation.group(1));

        boolean participant = conversationDao.findById(conversationId)
                .map(existing -> existing.hasParticipant(userId))
                .orElse(false);

        if (!participant) {
            log.warn("User {} tried to subscribe to conversation {} without being a participant",
                    userId, conversationId);
            throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
        }
    }

    private UUID currentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user.id();
        }
        throw new UnauthorizedException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
    }
}
