package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.constants.RealtimeMessages;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");

    private static final Pattern PRESENCE_TOPIC =
            Pattern.compile("^/topic/presence/([0-9a-fA-F-]{36})$");

    private static final String SUBSCRIBE_BUDGET = "stomp-subscribe";
    private static final String MESSAGE_BUDGET = "stomp-message";

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final ConversationDao conversationDao;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public StompAuthChannelInterceptor(JwtService jwtService, SessionService sessionService,
                                       ConversationDao conversationDao, RateLimiter rateLimiter,
                                       RateLimitProperties rateLimitProperties) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.conversationDao = conversationDao;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
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
            case SUBSCRIBE -> {
                if (!withinBudget(accessor, SUBSCRIBE_BUDGET, rateLimitProperties.getRealtimeSubscribe())) {
                    throw new TooManyRequestsException(RealtimeMessages.TOO_MANY_FRAMES);
                }
                authoriseSubscription(accessor);
            }
            case SEND -> {
                if (!withinBudget(accessor, MESSAGE_BUDGET, rateLimitProperties.getRealtimeMessage())) {
                    return null;
                }
            }
            default -> {
            }
        }

        return message;
    }

    private boolean withinBudget(StompHeaderAccessor accessor, String budget,
                                 RateLimitProperties.Bucket bucket) {
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            log.warn("Realtime frame without a session id skipped the {} budget", budget);
            return true;
        }

        if (rateLimiter.tryAcquire(budget + ":" + sessionId, bucket.getMaxAttempts(), bucket.getWindow())) {
            return true;
        }

        log.warn("Realtime session {} exceeded its {} budget", sessionId, budget);
        return false;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = BearerTokenExtractor.extractHeader(accessor.getFirstNativeHeader("Authorization"));

        UUID sessionId = jwtService.extractSessionId(token);
        Session session = sessionService.findActiveSessionById(sessionId);
        UserPrincipal principal = UserPrincipal.from(session.getDevice().getUser(), sessionId);

        accessor.setUser(new RealtimeAuthentication(principal));

        log.debug("Realtime connection authenticated for user {}", principal.id());
    }

    private void authoriseSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
        }

        if (destination.startsWith("/user/")) {
            return;
        }

        UUID userId = currentUserId(accessor.getUser());

        Matcher conversation = CONVERSATION_TOPIC.matcher(destination);
        if (conversation.matches()) {
            authoriseConversation(userId, UUID.fromString(conversation.group(1)));
            return;
        }

        Matcher presence = PRESENCE_TOPIC.matcher(destination);
        if (presence.matches()) {
            authorisePresence(userId, UUID.fromString(presence.group(1)));
            return;
        }

        log.warn("Realtime subscription to an unknown destination was refused: {}", destination);
        throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
    }

    private void authoriseConversation(UUID userId, UUID conversationId) {
        boolean participant = conversationDao.findById(conversationId)
                .map(existing -> existing.hasParticipant(userId))
                .orElse(false);

        if (!participant) {
            log.warn("User {} tried to subscribe to conversation {} without being a participant",
                    userId, conversationId);
            throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
        }
    }

    private void authorisePresence(UUID userId, UUID watchedId) {
        if (userId.equals(watchedId) || conversationDao.existsBetweenParticipants(userId, watchedId)) {
            return;
        }

        log.warn("User {} tried to watch presence of {} without sharing a conversation", userId, watchedId);
        throw new ForbiddenException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
    }

    private UUID currentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user.id();
        }
        throw new UnauthorizedException(MessagingMessages.SUBSCRIPTION_NOT_ALLOWED);
    }
}
