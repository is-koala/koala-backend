package com.exskylab.koala.core.realtime;

import com.exskylab.koala.business.abstracts.PresenceService;
import com.exskylab.koala.core.security.UserPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;

@Component
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final RealtimePublisher realtimePublisher;

    public PresenceEventListener(PresenceService presenceService, RealtimePublisher realtimePublisher) {
        this.presenceService = presenceService;
        this.realtimePublisher = realtimePublisher;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        principalOf(event.getUser()).ifPresent(user -> {
            boolean wasOffline = !presenceService.isOnline(user.id());
            presenceService.markOnline(user.id(), accessor.getSessionId());
            if (wasOffline) {
                realtimePublisher.presenceChanged(user.id(), true);
            }
        });
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        principalOf(event.getUser()).ifPresent(user -> {
            presenceService.markOffline(user.id(), event.getSessionId());
            if (!presenceService.isOnline(user.id())) {
                realtimePublisher.presenceChanged(user.id(), false);
            }
        });
    }

    private Optional<UserPrincipal> principalOf(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }
}
