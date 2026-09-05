package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.StaffTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffAuthenticationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void staffTokenProperties(DynamicPropertyRegistry registry) {
        registry.add("koala.staff.issuer-uri", StaffTokenIssuer::issuer);
        registry.add("koala.staff.jwk-set-uri", StaffTokenIssuer::jwkSetUri);
        registry.add("koala.staff.audience", StaffTokenIssuer::audience);
    }

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from staff_members where email like 'chain-%@iskoala.com'");
    }

    private String staffMember() {
        String subject = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into staff_members (id, keycloak_subject, email, display_name, is_active, created_at)
                values (?, ?, ?, ?, true, now())
                """, UUID.randomUUID(), subject, "chain-" + subject + "@iskoala.com", "Zincir Denemesi");
        return subject;
    }

    @Test
    void aStaffTokenCarryingTheRequiredPermissionPassesTheAdminGate() throws Exception {
        String token = StaffTokenIssuer.tokenFor(staffMember(), List.of("CATALOG_EDITOR"));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aStaffTokenWithoutThePermissionIsRefused() throws Exception {
        String token = StaffTokenIssuer.tokenFor(staffMember(), List.of("AUDITOR"));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void aValidTokenWithoutAStaffRecordAuthorizesNothing() throws Exception {
        String token = StaffTokenIssuer.tokenFor(UUID.randomUUID().toString(), List.of("OWNER"));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCategory").value("ACCESS_DENIED"));
    }

    @Test
    void permissionsComeFromKoalaNotFromTheToken() throws Exception {
        String subject = staffMember();
        jdbcTemplate.update("delete from staff_role_permissions where staff_role_id = "
                + "(select id from staff_roles where code = 'CATALOG_EDITOR') "
                + "and permission_code = 'category.manage'");

        try {
            String token = StaffTokenIssuer.tokenFor(subject, List.of("CATALOG_EDITOR"));

            mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                            + "select ?, id, 'category.manage', now() from staff_roles where code = 'CATALOG_EDITOR'",
                    UUID.randomUUID());
        }
    }

    @Test
    void anExpiredStaffTokenIsRejected() throws Exception {
        String token = StaffTokenIssuer.token(StaffTokenIssuer.issuer(), StaffTokenIssuer.audience(),
                staffMember(), List.of("OWNER"), Duration.ofSeconds(-60));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForAnotherAudienceIsRejected() throws Exception {
        String token = StaffTokenIssuer.token(StaffTokenIssuer.issuer(), "another-api",
                staffMember(), List.of("OWNER"), Duration.ofMinutes(5));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnUntrustedIssuerNeverEntersTheStaffChain() throws Exception {
        String token = StaffTokenIssuer.token("https://evil.example.com/realms/koala-staff",
                StaffTokenIssuer.audience(), staffMember(), List.of("OWNER"), Duration.ofMinutes(5));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAnonymousRequestNeverReachesTheAdminSurface() throws Exception {
        mockMvc.perform(get("/admin/job-categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theMarketplaceAdminBridgeStaysOpenDuringTheTransition() throws Exception {
        mockMvc.perform(get("/admin/job-categories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void anOrdinaryMarketplaceUserNeverReachesTheAdminSurface() throws Exception {
        mockMvc.perform(get("/admin/job-categories").with(user("worker").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnmappedAdminPathIsDeniedRatherThanGuessedAt() throws Exception {
        String token = StaffTokenIssuer.tokenFor(staffMember(), List.of("OWNER"));

        mockMvc.perform(get("/admin/not-a-real-endpoint").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
