package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffAuthenticationIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'chain-%@iskoala.com')");
        deleteStaffMembersLike("chain-%@iskoala.com");
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("chain-" + UUID.randomUUID() + "@iskoala.com", "Zincir Denemesi", roles);
    }

    @Test
    void aStaffTokenCarryingTheRequiredPermissionPassesTheAdminGate() throws Exception {
        mockMvc.perform(get("/admin/job-categories")
                        .header("Authorization", "Bearer " + tokenFor("CATALOG_EDITOR")))
                .andExpect(status().isOk());
    }

    @Test
    void aStaffTokenWithoutThePermissionIsRefused() throws Exception {
        mockMvc.perform(get("/admin/job-categories")
                        .header("Authorization", "Bearer " + tokenFor("AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void permissionsComeFromKoalaNotFromTheToken() throws Exception {
        String token = tokenFor("CATALOG_EDITOR");
        jdbcTemplate.update("delete from staff_role_permissions where staff_role_id = "
                + "(select id from staff_roles where code = 'CATALOG_EDITOR') "
                + "and permission_code = 'category.manage'");

        try {
            mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                            + "select ?, id, 'category.manage', now() from staff_roles where code = 'CATALOG_EDITOR'",
                    UUID.randomUUID());
        }
    }

    @Test
    void revokingTheMembershipTakesEffectOnTheVeryNextRequest() throws Exception {
        UUID staffId = createStaffMember("chain-" + UUID.randomUUID() + "@iskoala.com",
                "Zincir Denemesi", "CATALOG_EDITOR");
        String token = staffAccessToken(staffId);

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        jdbcTemplate.update("delete from staff_member_roles where staff_member_id = ?", staffId);

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void anExpiredStaffTokenIsRejected() throws Exception {
        UUID staffId = createStaffMember("chain-" + UUID.randomUUID() + "@iskoala.com", "Süresi Dolmuş", "OWNER");
        String token = jwtService.generateStaffToken(staffId, 0,
                JwtService.STAFF_ACCESS_AUDIENCE, Duration.ofSeconds(-60));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshTokenIsNotAnAccessToken() throws Exception {
        UUID staffId = createStaffMember("chain-" + UUID.randomUUID() + "@iskoala.com", "Yenileme", "OWNER");
        String refresh = jwtService.generateStaffToken(staffId, 0,
                JwtService.STAFF_REFRESH_AUDIENCE, Duration.ofHours(1));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMarketplaceUserTokenCanNeverProduceAStaffPrincipal() throws Exception {
        UUID staffId = createStaffMember("chain-" + UUID.randomUUID() + "@iskoala.com", "Hedef", "OWNER");

        User impersonated = new User();
        impersonated.setId(staffId);
        String marketplaceToken = jwtService.generateAccessToken(impersonated, UUID.randomUUID());

        mockMvc.perform(get("/admin/me").header("Authorization", "Bearer " + marketplaceToken))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + marketplaceToken))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aStaffTokenIsUselessOnTheMarketplaceChain() throws Exception {
        String token = tokenFor("OWNER");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aStaffMemberWhoMustChangeTheirPasswordReachesNothingElse() throws Exception {
        UUID staffId = createStaffMember("chain-" + UUID.randomUUID() + "@iskoala.com", "Yeni Personel", "OWNER");
        jdbcTemplate.update("update staff_members set must_change_password = true where id = ?", staffId);

        mockMvc.perform(get("/admin/job-categories")
                        .header("Authorization", "Bearer " + staffAccessToken(staffId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void anAnonymousRequestNeverReachesTheAdminSurface() throws Exception {
        mockMvc.perform(get("/admin/job-categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theMarketplaceAdminBridgeIsClosed() throws Exception {
        mockMvc.perform(get("/admin/job-categories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinaryMarketplaceUserNeverReachesTheAdminSurface() throws Exception {
        mockMvc.perform(get("/admin/job-categories").with(user("worker").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnmappedAdminPathIsDeniedRatherThanGuessedAt() throws Exception {
        mockMvc.perform(get("/admin/not-a-real-endpoint")
                        .header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void twoStaffTokensAreNeverTheSameString() throws Exception {
        assertNotEquals(tokenFor("OWNER"), tokenFor("OWNER"));
    }
}
