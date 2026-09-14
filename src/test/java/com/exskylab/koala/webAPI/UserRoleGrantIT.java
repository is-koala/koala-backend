package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class UserRoleGrantIT extends AbstractStaffIntegrationTest {

    private static final String PASSWORD = "GecerliParola1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private String adminToken;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        admin = saveUser("role-grant-admin@koala.test", Role.ROLE_USER, Role.ROLE_ADMIN);
        adminToken = staffTokenFor("role-grant-" + UUID.randomUUID() + "@iskoala.com",
                "Rol Yöneticisi", "OWNER");

        jdbcTemplate.update("delete from authorities where role = 'ROLE_ADMIN' "
                + "and user_id <> ? and user_id <> ?", admin.getId(), SystemUser.ID);
    }

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'role-grant-%@iskoala.com')");
        deleteStaffMembersLike("role-grant-%@iskoala.com");
    }

    @Test
    void aPlainUserIsRefusedByAllThreeEndpoints() throws Exception {
        User worker = saveUser("role-grant-worker@koala.test", Role.ROLE_USER);
        String token = login(worker.getEmail());

        mockMvc.perform(get("/admin/users/" + worker.getId() + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/" + worker.getId() + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/admin/users/" + worker.getId() + "/roles/ROLE_ADMIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingARoleRecordsWhenButNotWhoOnTheStaffPath() throws Exception {
        User target = saveUser("role-grant-target@koala.test", Role.ROLE_USER);

        grant(target.getId(), "ROLE_ADMIN").andExpect(status().isOk());

        assertNotNull(grantedAt(target.getId(), "ROLE_ADMIN"), "granted_at bos kaldi.");
        assertNull(grantedBy(target.getId(), "ROLE_ADMIN"),
                "authorities.granted_by_id users'a yabanci anahtar tasiyor; personelin orada satiri yok (ADR-0014)");

        mockMvc.perform(get("/admin/users/" + target.getId() + "/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.role == 'ROLE_ADMIN')].grantedAt").exists())
                .andExpect(jsonPath("$.data[?(@.role == 'ROLE_ADMIN')].grantedById")
                        .value(everyItem(nullValue())));
    }

    @Test
    void grantingTheSameRoleTwiceLeavesASingleRow() throws Exception {
        User target = saveUser("role-grant-idempotent@koala.test", Role.ROLE_USER);

        grant(target.getId(), "ROLE_ADMIN").andExpect(status().isOk());
        grant(target.getId(), "ROLE_ADMIN").andExpect(status().isOk());

        assertEquals(1, rowCount(target.getId(), "ROLE_ADMIN"));
        assertEquals(2, rowCount(target.getId()));
    }

    @Test
    void anotherAdminsRoleIsRevoked() throws Exception {
        User target = saveUser("role-grant-revoked@koala.test", Role.ROLE_USER, Role.ROLE_ADMIN);

        mockMvc.perform(delete("/admin/users/" + target.getId() + "/roles/ROLE_ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertEquals(0, rowCount(target.getId(), "ROLE_ADMIN"));
        assertEquals(1, rowCount(target.getId(), "ROLE_USER"));
    }

    @Test
    void theLastMarketplaceAdminCannotBeRevokedNowThatTheActorIsStaff() throws Exception {
        mockMvc.perform(delete("/admin/users/" + admin.getId() + "/roles/ROLE_ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertEquals(1, rowCount(admin.getId(), "ROLE_ADMIN"));
    }

    @Test
    void theMarketplaceMembershipRoleCannotBeTakenAwayHere() throws Exception {
        User target = saveUser("role-grant-membership@koala.test", Role.ROLE_USER);

        mockMvc.perform(delete("/admin/users/" + target.getId() + "/roles/ROLE_USER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertEquals(1, rowCount(target.getId(), "ROLE_USER"));
    }

    @Test
    void theSystemUserRolesAreImmutable() throws Exception {
        grant(SystemUser.ID, "ROLE_USER").andExpect(status().isConflict());

        mockMvc.perform(delete("/admin/users/" + SystemUser.ID + "/roles/ROLE_ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertEquals(1, rowCount(SystemUser.ID, "ROLE_ADMIN"));
    }

    @Test
    void anUnknownUserIdIsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/admin/users/" + missing + "/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        grant(missing, "ROLE_ADMIN").andExpect(status().isNotFound());

        mockMvc.perform(delete("/admin/users/" + missing + "/roles/ROLE_ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownRoleNameIsARejectedRequestNotAServerError() throws Exception {
        User target = saveUser("role-grant-unknown-role@koala.test", Role.ROLE_USER);

        grant(target.getId(), "ROLE_SUPERUSER").andExpect(status().isBadRequest());

        mockMvc.perform(delete("/admin/users/" + target.getId() + "/roles/ROLE_SUPERUSER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions grant(UUID userId, String role) throws Exception {
        return mockMvc.perform(post("/admin/users/" + userId + "/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\"}"));
    }

    private User saveUser(String email, Role... roles) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setAuthorities(new HashSet<>(Set.copyOf(List.of(roles))));
        return userDao.saveAndFlush(user);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "role-grant-it-device")
                        .header("User-Agent", "Koala Role Grant IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("token").asText();
    }

    private Object grantedAt(UUID userId, String role) {
        return jdbcTemplate.queryForObject(
                "select granted_at from authorities where user_id = ? and role = ?",
                java.sql.Timestamp.class, userId, role);
    }

    private UUID grantedBy(UUID userId, String role) {
        return jdbcTemplate.queryForObject(
                "select granted_by_id from authorities where user_id = ? and role = ?",
                UUID.class, userId, role);
    }

    private int rowCount(UUID userId, String role) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ? and role = ?", Integer.class, userId, role);
        return found == null ? 0 : found;
    }

    private int rowCount(UUID userId) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ?", Integer.class, userId);
        return found == null ? 0 : found;
    }
}
