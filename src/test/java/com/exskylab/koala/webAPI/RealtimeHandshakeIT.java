package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.Session;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "realtime.origins.production=https://iskoala.com",
        "realtime.origins.staging=https://staging.iskoala.com"
})
class RealtimeHandshakeIT extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://iskoala.com";
    private static final String UNKNOWN_ORIGIN = "https://iskoala.com.evil.example";
    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private SessionDao sessionDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketStompClient stompClient;
    private UUID userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new SimpleMessageConverter());

        User user = new User();
        user.setFirstName("Realtime");
        user.setLastName("Handshake");
        user.setEmail("realtime-handshake-" + UUID.randomUUID() + "@koala.test");
        user.setPassword("not-used");
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        user = userDao.saveAndFlush(user);
        userId = user.getId();

        Device device = new Device();
        device.setUser(user);
        device.setType(DeviceType.WEB_BROWSER);
        device.setName("Realtime browser");
        device.setClientDeviceId("realtime-" + UUID.randomUUID());
        device.setFirstSeenAt(Instant.now());
        device.setLastSeenAt(Instant.now());
        device = deviceDao.saveAndFlush(device);

        Session session = new Session();
        session.setDevice(device);
        session.setRefreshTokenHash("not-used");
        session.setRefreshExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        session.setIpAddress("127.0.0.1");
        session.setLastAccessedAt(Instant.now());
        session.setActive(true);
        session = sessionDao.saveAndFlush(session);

        accessToken = jwtService.generateAccessToken(user, session.getId());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void aBrowserFromAnAllowedOriginCompletesTheHandshake() throws Exception {
        StompSession session = connect(ALLOWED_ORIGIN, accessToken);

        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    void aBrowserFromAnUnknownOriginNeverReachesTheStompLayer() {
        assertThrows(ExecutionException.class, () -> connect(UNKNOWN_ORIGIN, accessToken));
    }

    @Test
    void aNativeClientCarryingNoOriginStillConnects() throws Exception {
        StompSession session = connect(null, accessToken);

        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    void anInvalidTokenNeverEstablishesTheSession() {
        assertThrows(ExecutionException.class, () -> connect(ALLOWED_ORIGIN, "not-a-jwt"));
    }

    @Test
    void aRefusedSubscriptionLeavesTheConnectionOpenAndAnswersOnThatSubscription() throws Exception {
        StompSession session = connect(ALLOWED_ORIGIN, accessToken);

        BlockingQueue<byte[]> refusals = new LinkedBlockingQueue<>();
        session.subscribe("/topic/conversations/" + UUID.randomUUID(), collectInto(refusals));

        byte[] refusal = refusals.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(refusal, "Yetkisiz abonelik icin abonelik basina bir ret karesi bekleniyordu.");

        JsonNode event = objectMapper.readTree(refusal);
        assertEquals("ERROR", event.get("type").asString());
        assertEquals("MESSAGING_SUBSCRIPTION_NOT_ALLOWED", event.get("error").get("errorCode").asString());
        assertFalse(event.get("error").get("message").asString().startsWith("{"));

        assertTrue(session.isConnected(), "Yetkisiz abonelik baglantiyi dusurmemeli.");

        BlockingQueue<byte[]> presence = new LinkedBlockingQueue<>();
        session.subscribe("/topic/presence/" + userId, collectInto(presence));

        assertNull(presence.poll(1, TimeUnit.SECONDS),
                "Izinli aboneligin ret karesi almamasi gerekir.");
        assertTrue(session.isConnected(), "Izinli abonelik ret sonrasinda da calismali.");

        session.disconnect();
    }

    private StompFrameHandler collectInto(BlockingQueue<byte[]> sink) {
        return new StompFrameHandler() {
            @Override
            public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                sink.add(payload instanceof byte[] bytes
                        ? bytes : String.valueOf(payload).getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private StompSession connect(String origin, String token) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        if (origin != null) {
            handshakeHeaders.add("Origin", origin);
        }

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders,
                        connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
