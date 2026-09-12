package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.Session;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityMultipartBudgetIT extends AbstractIntegrationTest {

    private static final int MEBIBYTE = 1024 * 1024;
    private static final String[] PHOTO_PARTS = {"selfie", "front", "back", "together"};

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

    @MockitoBean
    private IdentityDocumentReviewService identityDocumentReviewService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String accessToken;
    private String clientDeviceId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFirstName("Kimlik");
        user.setLastName("Yukleme");
        user.setEmail("identity-budget-" + UUID.randomUUID() + "@koala.test");
        user.setPassword("not-used");
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        user = userDao.saveAndFlush(user);

        Device device = new Device();
        device.setUser(user);
        device.setType(DeviceType.WEB_BROWSER);
        device.setName("Kimlik yukleme testi");
        clientDeviceId = "identity-budget-" + UUID.randomUUID();
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

        accessToken = jwtService.generateAccessToken(user, session.getId());

        IdentityVerification pending = new IdentityVerification();
        pending.setId(UUID.randomUUID());
        pending.setFirstName("Kimlik");
        pending.setLastName("Yukleme");
        pending.setTcIdentityNumber("10000000146");
        pending.setTcDocumentNumber("A12B34567");
        pending.setCreatedAt(Instant.now());
        when(identityDocumentReviewService.submit(any(), any(), any(), any(), any()))
                .thenReturn(pending);
    }

    @Test
    void fourMaximumSizedPhotosFitInsideTheIdentityRequestBudget() throws Exception {
        HttpResponse<String> response = sendIdentityRequest(10 * MEBIBYTE);

        assertEquals(201, response.statusCode(), response.body());
        verify(identityDocumentReviewService).submit(any(), any(), any(), any(), any());
    }

    @Test
    void aMultipartRequestBeyondTheTransportBudgetUsesTheStableErrorEnvelope() throws Exception {
        HttpResponse<String> response = sendIdentityRequest(11 * MEBIBYTE);

        assertEquals(413, response.statusCode(), response.body());
        org.assertj.core.api.Assertions.assertThat(response.body())
                .contains("\"errorCode\":\"REQUEST_BODY_TOO_LARGE\"")
                .contains("\"errorCategory\":\"REQUEST_MALFORMED\"")
                .contains("İstek gövdesi izin verilen boyutu aşıyor.");
    }

    private HttpResponse<String> sendIdentityRequest(int bytesPerPhoto) throws Exception {
        String boundary = "koala-identity-" + UUID.randomUUID();
        List<HttpRequest.BodyPublisher> publishers = new ArrayList<>();
        addIdentityPart(publishers, boundary);

        byte[] photo = new byte[bytesPerPhoto];
        for (String part : PHOTO_PARTS) {
            addPhotoPart(publishers, boundary, part, photo);
        }
        publishers.add(text("--" + boundary + "--\r\n"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/users/me/identity-documents"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Client-Platform", "web")
                .header("X-Device-Id", clientDeviceId)
                .header(HttpHeaders.USER_AGENT, "Koala identity multipart budget test")
                .POST(HttpRequest.BodyPublishers.concat(publishers.toArray(HttpRequest.BodyPublisher[]::new)))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void addIdentityPart(List<HttpRequest.BodyPublisher> publishers, String boundary) {
        publishers.add(text("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"identity\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + "{\"firstName\":\"Kimlik\",\"lastName\":\"Yukleme\","
                + "\"birthDate\":\"1990-01-01\",\"tcIdentityNumber\":\"10000000146\","
                + "\"tcDocumentNumber\":\"A12B34567\"}\r\n"));
    }

    private static void addPhotoPart(List<HttpRequest.BodyPublisher> publishers, String boundary,
                                     String part, byte[] content) {
        publishers.add(text("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + part + "\"; filename=\"" + part + ".jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n"));
        publishers.add(HttpRequest.BodyPublishers.ofByteArray(content));
        publishers.add(text("\r\n"));
    }

    private static HttpRequest.BodyPublisher text(String value) {
        return HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8);
    }
}
