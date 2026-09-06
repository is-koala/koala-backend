package com.exskylab.koala.dataAccess;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoritiesGrantProvenanceIT extends AbstractIntegrationTest {

    private static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDao userDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void addingARoleToTheManagedCollectionKeepsTheEarlierGrantIntact() {
        UUID userId = persistUserWith("provenance-add@example.invalid", Role.ROLE_USER);
        stampProvenance(userId, "ROLE_USER");

        User reloaded = userDao.findById(userId).orElseThrow();
        reloaded.getAuthorities().add(Role.ROLE_ADMIN);
        entityManager.flush();

        assertNotNull(grantedAt(userId, "ROLE_USER"),
                "Koleksiyona rol eklemek mevcut satirin granted_at degerini sildi.");
        assertEquals(UUID.fromString(SYSTEM_USER_ID), grantedBy(userId, "ROLE_USER"),
                "Koleksiyona rol eklemek mevcut satirin granted_by_id degerini sildi.");
        assertEquals(2, roleCount(userId));
    }

    @Test
    @Transactional
    void replacingTheCollectionWipesTheGrantProvenance() {
        UUID userId = persistUserWith("provenance-replace@example.invalid", Role.ROLE_USER);
        stampProvenance(userId, "ROLE_USER");

        User reloaded = userDao.findById(userId).orElseThrow();
        reloaded.setAuthorities(new HashSet<>(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN)));
        entityManager.flush();

        assertEquals(2, roleCount(userId));
        assertNull(grantedAt(userId, "ROLE_USER"),
                "Koleksiyonun toptan degistirilmesi rol verme kaydini artik silmiyor; karar gozden gecirilmeli.");
        assertNull(grantedBy(userId, "ROLE_USER"),
                "Koleksiyonun toptan degistirilmesi rol verme kaydini artik silmiyor; karar gozden gecirilmeli.");
    }

    @Test
    @Transactional
    void rolesStillRoundTripThroughTheMappingAndIntoThePrincipal() {
        UUID userId = persistUserWith("provenance-principal@example.invalid", Role.ROLE_USER, Role.ROLE_ADMIN);
        entityManager.flush();
        entityManager.clear();

        User reloaded = userDao.findById(userId).orElseThrow();
        UserPrincipal principal = UserPrincipal.from(reloaded);

        assertEquals(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN), principal.roles());
        assertTrue(principal.hasRole(Role.ROLE_ADMIN));
        assertEquals(2, principal.getAuthorities().size());
    }

    private UUID persistUserWith(String email, Role... roles) {
        User user = new User();
        user.setEmail(email);
        user.setAuthorities(new HashSet<>(List.of(roles)));
        UUID userId = userDao.saveAndFlush(user).getId();
        entityManager.flush();
        return userId;
    }

    private void stampProvenance(UUID userId, String role) {
        entityManager.flush();
        jdbcTemplate.update("""
                update authorities set granted_at = ?, granted_by_id = ?::uuid
                where user_id = ? and role = ?
                """, java.sql.Timestamp.from(Instant.now()), SYSTEM_USER_ID, userId, role);
    }

    private Instant grantedAt(UUID userId, String role) {
        return jdbcTemplate.queryForObject(
                "select granted_at from authorities where user_id = ? and role = ?",
                (rs, rowNumber) -> {
                    java.sql.Timestamp value = rs.getTimestamp("granted_at");
                    return value == null ? null : value.toInstant();
                }, userId, role);
    }

    private UUID grantedBy(UUID userId, String role) {
        return jdbcTemplate.queryForObject(
                "select granted_by_id from authorities where user_id = ? and role = ?",
                UUID.class, userId, role);
    }

    private int roleCount(UUID userId) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ?", Integer.class, userId);
        return found == null ? 0 : found;
    }
}
