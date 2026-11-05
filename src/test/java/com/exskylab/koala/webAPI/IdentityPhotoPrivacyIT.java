package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdentityPhotoPrivacyIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL_PREFIX = "identity-privacy-";
    private static final String GPS_MARKER = "KOALA-GPS-41.0082N-28.9784E";
    private static final String[] SLOTS = {"selfie", "front", "back", "together"};
    private static final String[] COLUMNS = {
            "selfie_document_id", "verification_document_id", "back_document_id", "together_document_id"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StorageService storageService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final List<String> storedKeys = new ArrayList<>();
    private final List<UUID> mediaIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc.update("delete from admin_audit_entries");
        insertUser(ownerId);
        insertUser(strangerId);
    }

    @AfterEach
    void tearDown() {
        mediaIds.addAll(jdbc.queryForList("select unnest(array[" + String.join(", ", COLUMNS) + "]) "
                + "from user_verifications where user_id = ?", UUID.class, ownerId));
        jdbc.update("delete from user_verifications where user_id = ?", ownerId);
        for (UUID mediaId : mediaIds) {
            if (mediaId != null) {
                storedKeys.addAll(jdbc.queryForList("select file_url from media where id = ? and file_url is not null",
                        String.class, mediaId));
                jdbc.update("delete from media where id = ?", mediaId);
            }
        }
        for (String key : storedKeys) {
            storageService.deleteFile(key);
        }
        jdbc.update("delete from admin_audit_entries");
        deleteStaffMembersLike(STAFF_EMAIL_PREFIX + "%");
        jdbc.update("delete from users where id in (?, ?)", ownerId, strangerId);
    }

    @Test
    void uploadedIdentityPhotosAreStoredPrivatelyWithoutTheirExifSegment() throws Exception {
        submitIdentityPhotos();

        List<String> keys = documentKeys();
        assertEquals(4, keys.size());
        for (String key : keys) {
            assertTrue(key.startsWith("identity-documents/"), key);
            byte[] stored = storageService.readFile(key);
            assertFalse(contains(stored, GPS_MARKER), "EXIF payload reached storage for " + key);
            assertFalse(contains(stored, "Exif"), "APP1 Exif segment reached storage for " + key);
        }
    }

    @Test
    void nobodyReachesAnIdentityPhotoThroughThePublicOrUserFacingStorageRoutes() throws Exception {
        submitIdentityPhotos();

        for (String key : documentKeys()) {
            String objectName = key.substring(key.indexOf('/') + 1);
            for (String path : List.of("/uploads/" + key, "/uploads/images/" + objectName,
                    "/uploads/public/" + objectName, "/uploads/files/" + objectName)) {
                mockMvc.perform(get(path)).andExpect(refused());
                mockMvc.perform(get(path).with(user(principal(strangerId)))).andExpect(refused());
                mockMvc.perform(get(path).with(user(principal(ownerId)))).andExpect(refused());
            }
        }
    }

    @Test
    void aLegacyIdentityPhotoLeftUnderImagesIsNoLongerPublicWhileProfileImagesStillAre() throws Exception {
        UUID reviewId = legacyReview();
        String legacyKey = jdbc.queryForObject("select m.file_url from media m join user_verifications v "
                + "on v.verification_document_id = m.id where v.id = ?", String.class, reviewId);
        assertTrue(legacyKey.startsWith("images/"), legacyKey);

        String profileKey = store(png(), "profil.png", "image/png", FolderType.IMAGE);

        mockMvc.perform(get("/uploads/" + legacyKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/" + legacyKey).with(user(principal(strangerId))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/" + profileKey)).andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(storageService.readFile(profileKey),
                        result.getResponse().getContentAsByteArray()));
    }

    @Test
    void aSoftDeletedLegacyIdentityPhotoStaysHiddenFromThePublicImageRoute() throws Exception {
        UUID reviewId = legacyReview();
        String legacyKey = (String) frontDocument(reviewId).get("file_url");
        jdbc.update("update media set is_deleted = true, deleted_at = now() where file_url = ?", legacyKey);

        mockMvc.perform(get("/uploads/" + legacyKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/" + legacyKey + "/")).andExpect(status().isNotFound());
    }

    @Test
    void aTrailingSlashDoesNotSlipALegacyIdentityPhotoPastTheCheck() throws Exception {
        UUID reviewId = legacyReview();
        String legacyKey = (String) frontDocument(reviewId).get("file_url");

        mockMvc.perform(get("/uploads/" + legacyKey + "/")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/" + legacyKey + "/").with(user(principal(strangerId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStaffMemberHoldingIdentityReviewDownloadsThePhotoAndTheReadIsAudited() throws Exception {
        submitIdentityPhotos();
        UUID reviewId = reviewOf(ownerId);
        Map<String, Object> front = frontDocument(reviewId);
        UUID staffId = createStaffMember(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com",
                "Trust Lideri", "TRUST_LEAD");

        byte[] body = mockMvc.perform(get("/admin/identity-verifications/{reviewId}/documents/{documentId}/download",
                        reviewId, front.get("id"))
                        .header("Authorization", "Bearer " + staffAccessToken(staffId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(storageService.readFile((String) front.get("file_url")), body);
        assertEquals(1, jdbc.queryForObject("select count(*) from admin_audit_entries "
                        + "where action = 'identity.document.download' and allowed = true and actor_staff_id = ?",
                Integer.class, staffId));
    }

    @Test
    void aStaffMemberWithoutIdentityReviewIsRefusedAndTheAttemptIsAudited() throws Exception {
        submitIdentityPhotos();
        UUID reviewId = reviewOf(ownerId);
        Map<String, Object> front = frontDocument(reviewId);
        UUID staffId = createStaffMember(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com",
                "Şirket Onaycısı", "COMPANY_APPROVER");

        mockMvc.perform(get("/admin/identity-verifications/{reviewId}/documents/{documentId}/download",
                        reviewId, front.get("id"))
                        .header("Authorization", "Bearer " + staffAccessToken(staffId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/identity-verifications/{reviewId}/documents/{documentId}/download",
                        reviewId, front.get("id"))
                        .with(user(principal(ownerId))))
                .andExpect(refused());

        assertEquals(1, jdbc.queryForObject("select count(*) from admin_audit_entries "
                        + "where action = 'identity.document.download' and allowed = false and actor_staff_id = ?",
                Integer.class, staffId));
    }

    @Test
    void aStaffMemberStillReadsALegacyPhotoStoredUnderImages() throws Exception {
        UUID reviewId = legacyReview();
        Map<String, Object> front = frontDocument(reviewId);
        String token = staffTokenFor(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com",
                "Trust Lideri", "TRUST_LEAD");

        mockMvc.perform(get("/admin/identity-verifications/{reviewId}/documents/{documentId}/download",
                        reviewId, front.get("id"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitIdentityPhotos() throws Exception {
        String identity = """
                {"firstName":"Kimlik","lastName":"Gizlilik","birthDate":"1998-04-12",
                 "tcIdentityNumber":"10000000146","tcDocumentNumber":"A01A00000"}
                """;
        var request = multipart("/users/me/identity-documents")
                .file(new MockMultipartFile("identity", "", MediaType.APPLICATION_JSON_VALUE,
                        identity.getBytes(StandardCharsets.UTF_8)));
        for (String slot : SLOTS) {
            request.file(new MockMultipartFile(slot, slot + ".jpg", MediaType.IMAGE_JPEG_VALUE,
                    jpegWithExif(GPS_MARKER)));
        }
        mockMvc.perform(request.with(user(principal(ownerId)))).andExpect(status().isCreated());
    }

    private UUID legacyReview() throws IOException {
        UUID[] documents = new UUID[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            String key = store(jpegWithExif(GPS_MARKER), SLOTS[i] + ".jpg", MediaType.IMAGE_JPEG_VALUE,
                    FolderType.IMAGE);
            UUID mediaId = UUID.randomUUID();
            mediaIds.add(mediaId);
            jdbc.update("insert into media (id, media_type, created_at, is_deleted, file_name, file_type, "
                            + "file_url, file_size) values (?, 'VERIFICATION_DOCUMENT', now(), false, ?, "
                            + "'image/jpeg', ?, 3)",
                    mediaId, SLOTS[i] + ".jpg", key);
            documents[i] = mediaId;
        }
        UUID reviewId = UUID.randomUUID();
        jdbc.update("insert into user_verifications (id, verification_type, created_at, is_deleted, is_verified, "
                        + "user_id, tc_identity_number, first_name, last_name, birth_date, tc_document_number, "
                        + "selfie_document_id, verification_document_id, back_document_id, together_document_id) "
                        + "values (?, 'IDENTITY', now(), false, false, ?, '10000000146', 'kimlik', 'gizlilik', "
                        + "date '1998-04-12', 'A01A00000', ?, ?, ?, ?)",
                reviewId, ownerId, documents[0], documents[1], documents[2], documents[3]);
        return reviewId;
    }

    private String store(byte[] bytes, String name, String contentType, FolderType folderType) {
        String key = storageService.uploadFile(bytes, name, contentType, folderType);
        storedKeys.add(key);
        return key;
    }

    private UUID reviewOf(UUID userId) {
        return jdbc.queryForObject("select id from user_verifications where user_id = ?", UUID.class, userId);
    }

    private Map<String, Object> frontDocument(UUID reviewId) {
        return jdbc.queryForMap("select m.id, m.file_url from media m join user_verifications v "
                + "on v.verification_document_id = m.id where v.id = ?", reviewId);
    }

    private List<String> documentKeys() {
        UUID reviewId = reviewOf(ownerId);
        List<String> keys = new ArrayList<>();
        for (String column : COLUMNS) {
            keys.add(jdbc.queryForObject("select m.file_url from media m join user_verifications v on v."
                    + column + " = m.id where v.id = ?", String.class, reviewId));
        }
        return keys;
    }

    private static ResultMatcher refused() {
        return result -> {
            int code = result.getResponse().getStatus();
            assertTrue(code == 401 || code == 403 || code == 404,
                    "Identity photo route must refuse, got " + code + " for " + result.getRequest().getRequestURI());
        };
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                + "email_verified, phone_verified, identity_verified) "
                + "values (?, now(), false, ?, 'test', false, true, false, false)", id, id + "@example.com");
    }

    private static UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 9, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] jpegWithExif(String marker) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "jpg", encoded);
        byte[] jpeg = encoded.toByteArray();

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write("Exif".getBytes(StandardCharsets.US_ASCII));
        payload.write(new byte[]{0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0});
        payload.write(marker.getBytes(StandardCharsets.US_ASCII));
        int length = payload.size() + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.write(payload.toByteArray());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }
}
