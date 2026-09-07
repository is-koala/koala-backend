package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffLoginIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'login-%@iskoala.com')");
        jdbcTemplate.update("delete from staff_members where email like 'login-%@iskoala.com'");
    }

    private String email() {
        return "login-" + UUID.randomUUID() + "@iskoala.com";
    }

    private ResultActions attempt(String address, String password) throws Exception {
        return mockMvc.perform(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", address, "password", password))));
    }

    private String accessTokenOf(ResultActions actions) throws Exception {
        Map<String, Object> body = objectMapper.readValue(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {
                });
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("accessToken");
    }

    private String envelope(ResultActions actions) throws Exception {
        Map<String, Object> parsed = new TreeMap<>(objectMapper.readValue(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString();
    }

    @Test
    void aStaffMemberSignsInWithEmailAndPasswordAndReachesTheirOwnPermissions() throws Exception {
        String address = email();
        createStaffMember(address, "Katalog Editörü", "CATALOG_EDITOR");

        String token = accessTokenOf(attempt(address, STAFF_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false)));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/disputes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWrongPasswordIsIndistinguishableFromAnAccountThatDoesNotExist() throws Exception {
        String address = email();
        createStaffMember(address, "Var Olan", "OWNER");

        String wrongPassword = envelope(attempt(address, "kesinlikle-yanlis-parola"));
        String absentAccount = envelope(attempt(email(), "kesinlikle-yanlis-parola"));

        assertEquals(absentAccount, wrongPassword,
                "Yanlış parola ile olmayan hesabın yanıtı ayırt edilebiliyor");
    }

    @Test
    void bothBranchesCarryTheSameErrorCode() throws Exception {
        String address = email();
        createStaffMember(address, "Var Olan", "OWNER");

        attempt(address, "yanlis").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("STAFF_LOGIN_FAILED"));
        attempt(email(), "yanlis").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("STAFF_LOGIN_FAILED"));
    }

    @Test
    void aDisabledAccountIsIndistinguishableTooAndItsPasswordStillDoesNotWork() throws Exception {
        String address = email();
        UUID staffId = createStaffMember(address, "Kapalı", "OWNER");
        jdbcTemplate.update("update staff_members set is_active = false where id = ?", staffId);

        assertEquals(envelope(attempt(email(), STAFF_PASSWORD)), envelope(attempt(address, STAFF_PASSWORD)));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndTheRightPasswordStopsWorking() throws Exception {
        String address = email();
        UUID staffId = createStaffMember(address, "Kilitlenecek", "OWNER");

        for (int attempt = 0; attempt < 5; attempt++) {
            attempt(address, "yanlis-" + attempt).andExpect(status().isUnauthorized());
        }

        assertNotNull(jdbcTemplate.queryForObject(
                        "select locked_until from staff_members where id = ?", java.sql.Timestamp.class, staffId),
                "Beşinci başarısız denemeden sonra hesap kilitlenmeliydi");

        attempt(address, STAFF_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void theLockLeavesNoTraceInTheResponseEither() throws Exception {
        String address = email();
        createStaffMember(address, "Kilitlenecek", "OWNER");

        for (int attempt = 0; attempt < 5; attempt++) {
            attempt(address, "yanlis-" + attempt);
        }

        assertEquals(envelope(attempt(email(), STAFF_PASSWORD)), envelope(attempt(address, STAFF_PASSWORD)),
                "Kilitli hesap yanıtı var olmayan hesabınkinden ayırt edilebiliyor");
    }

    @Test
    void aSuccessfulSignInClearsTheFailureCounter() throws Exception {
        String address = email();
        UUID staffId = createStaffMember(address, "Toparlanan", "OWNER");

        attempt(address, "yanlis").andExpect(status().isUnauthorized());
        attempt(address, STAFF_PASSWORD).andExpect(status().isOk());

        assertEquals(0, jdbcTemplate.queryForObject(
                "select failed_login_attempts from staff_members where id = ?", Integer.class, staffId));
    }

    @Test
    void loggingOutInvalidatesTheTokenThatAskedForIt() throws Exception {
        String address = email();
        createStaffMember(address, "Çıkacak", "CATALOG_EDITOR");
        String token = accessTokenOf(attempt(address, STAFF_PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(post("/admin/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshTokenBuysANewAccessTokenAndTheOldRefreshTokenDiesWithLogout() throws Exception {
        String address = email();
        createStaffMember(address, "Yenileyen", "CATALOG_EDITOR");

        Map<String, Object> body = objectMapper.readValue(
                attempt(address, STAFF_PASSWORD).andReturn().getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {
                });
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        String refreshToken = (String) data.get("refreshToken");

        String refreshed = accessTokenOf(mockMvc.perform(post("/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk()));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + refreshed))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/auth/logout").header("Authorization", "Bearer " + refreshed))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshAccountMustChangeItsPasswordBeforeReachingAnythingElse() throws Exception {
        String address = email();
        UUID staffId = createStaffMember(address, "İlk Giriş", "CATALOG_EDITOR");
        jdbcTemplate.update("update staff_members set must_change_password = true where id = ?", staffId);

        String token = accessTokenOf(attempt(address, STAFF_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true)));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", STAFF_PASSWORD,
                                "newPassword", "Yeni-Personel-Parolasi-2026"))))
                .andExpect(status().isOk());

        String afterChange = accessTokenOf(attempt(address, "Yeni-Personel-Parolasi-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false)));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + afterChange))
                .andExpect(status().isOk());
    }

    @Test
    void changingThePasswordInvalidatesEveryTokenIncludingTheOneThatChangedIt() throws Exception {
        String address = email();
        createStaffMember(address, "Parola Değiştiren", "CATALOG_EDITOR");
        String token = accessTokenOf(attempt(address, STAFF_PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(post("/admin/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", STAFF_PASSWORD,
                                "newPassword", "Baska-Bir-Parola-2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theChangePasswordEndpointStillChecksTheCurrentPassword() throws Exception {
        String address = email();
        createStaffMember(address, "Yanlış Deneyen", "CATALOG_EDITOR");
        String token = accessTokenOf(attempt(address, STAFF_PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(post("/admin/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "yanlis",
                                "newPassword", "Yeni-Bir-Parola-2026"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aShortPasswordIsRefusedByTheDtoWithAFieldName() throws Exception {
        String address = email();
        createStaffMember(address, "Kısa Parola", "CATALOG_EDITOR");
        String token = accessTokenOf(attempt(address, STAFF_PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(post("/admin/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", STAFF_PASSWORD,
                                "newPassword", "kisa"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("newPassword"));
    }

    @Test
    void anAnonymousCallerCannotLogSomeoneElseOut() throws Exception {
        mockMvc.perform(post("/admin/auth/logout")).andExpect(status().isUnauthorized());
    }
}
