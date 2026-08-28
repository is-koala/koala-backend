package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.RealtimeProperties;
import com.exskylab.koala.core.realtime.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final int MAX_BUFFER_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final RealtimeProperties realtimeProperties;
    private final CorsProperties corsProperties;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                           RealtimeProperties realtimeProperties,
                           CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.realtimeProperties = realtimeProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(realtimeProperties.getEndpoint())
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_MESSAGE_BYTES);
        registration.setSendBufferSizeLimit(MAX_BUFFER_BYTES);
        registration.setSendTimeLimit(SEND_TIME_LIMIT_MILLIS);
    }
}
