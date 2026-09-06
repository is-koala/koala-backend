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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "realtime.origins.production=https://iskoala.com",
        "realtime.origins.staging=https://staging.iskoala.com"
})
class RealtimeHandshakeIT extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://iskoala.com";
    private static final String UNKNOWN_ORIGIN = "https://iskoala.com.evil.example";
    private static final long TIMEOUT_SECONDS = 10;
    private static final String JWT_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2VkLWluLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OWFiY2RlZg==";

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

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketStompClient stompClient;
    private UUID userId;
    private String accessToken;
    private String clientDeviceId;
    private UUID persistedSessionId;

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
        clientDeviceId = "realtime-" + UUID.randomUUID();
        device.setClientDeviceId(clientDeviceId);
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
        persistedSessionId = session.getId();

        accessToken = jwtService.generateAccessToken(user, persistedSessionId);
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
    void anAuthenticatedSessionCanIssueANonCacheableRealtimeCredential() throws Exception {
        Instant requestedAt = Instant.now();
        MvcResult result = mockMvc.perform(realtimeCredentialRequest())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Instant expiresAt = Instant.parse(data.get("expiresAt").asString());
        assertTrue(expiresAt.isAfter(requestedAt.plusSeconds(60)));
        assertTrue(expiresAt.isBefore(requestedAt.plusSeconds(120)));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .build()
                .parseSignedClaims(data.get("token").asString())
                .getPayload();
        assertEquals(Set.of("stomp"), claims.getAudience());
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(persistedSessionId.toString(), claims.getId());
    }

    @Test
    void aRealtimeCredentialCannotAuthenticateAnHttpRequest() throws Exception {
        String realtimeCredential = issueRealtimeCredential();

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + realtimeCredential)
                        .header("X-Client-Platform", "web")
                        .header("X-Device-Id", clientDeviceId)
                        .header(HttpHeaders.USER_AGENT, "Koala realtime test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRealtimeCredentialCompletesStompConnect() throws Exception {
        StompSession session = connect(ALLOWED_ORIGIN, issueRealtimeCredential());

        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    void aCredentialWithAnotherAudienceCannotConnect() {
        String token = signedToken(userId, persistedSessionId, "not-stomp",
                Instant.now().plusSeconds(60));

        assertThrows(ExecutionException.class, () -> connect(ALLOWED_ORIGIN, token));
    }

    @Test
    void aCredentialCannotClaimAnotherUserInItsSession() {
        String token = signedToken(UUID.randomUUID(), persistedSessionId, "stomp",
                Instant.now().plusSeconds(60));

        assertThrows(ExecutionException.class, () -> connect(ALLOWED_ORIGIN, token));
    }

    @Test
    void anExpiredRealtimeCredentialCannotConnect() {
        String token = signedToken(userId, persistedSessionId, "stomp",
                Instant.now().minusSeconds(1));

        assertThrows(ExecutionException.class, () -> connect(ALLOWED_ORIGIN, token));
    }

    @Test
    void aCredentialForARevokedSessionCannotConnect() throws Exception {
        String token = issueRealtimeCredential();
        Session revoked = sessionDao.findById(persistedSessionId).orElseThrow();
        revoked.setActive(false);
        sessionDao.saveAndFlush(revoked);

        assertThrows(ExecutionException.class, () -> connect(ALLOWED_ORIGIN, token));
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

    private String issueRealtimeCredential() throws Exception {
        MvcResult result = mockMvc.perform(realtimeCredentialRequest())
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asString();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder realtimeCredentialRequest() {
        return post("/users/me/realtime-credential")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Client-Platform", "web")
                .header("X-Device-Id", clientDeviceId)
                .header(HttpHeaders.USER_AGENT, "Koala realtime test");
    }

    private String signedToken(UUID subject, UUID sessionId, String audience, Instant expiresAt) {
        return Jwts.builder()
                .subject(subject.toString())
                .id(sessionId.toString())
                .audience().add(audience)
                .and()
                .issuedAt(Date.from(expiresAt.minusSeconds(60)))
                .expiration(Date.from(expiresAt))
                .issuer("iskoala.com")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)), Jwts.SIG.HS256)
                .compact();
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
