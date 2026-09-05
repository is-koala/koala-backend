package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.AdminBootstrapProperties;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AdminBootstrapIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID persistWorker(String email) {
        User user = new User();
        user.setEmail(email);
        user.setAuthorities(new HashSet<>(Set.of(Role.ROLE_USER)));
        return userDao.saveAndFlush(user).getId();
    }

    @Test
    void theSecondRunWritesNothingAndTheRowKeepsItsFirstGrant() {
        UUID userId = persistWorker("bootstrap-idempotent@koala.test");

        assertTrue(userService.bootstrapAdminRole("bootstrap-idempotent@koala.test"));
        Object firstGrantedAt = grantedAt(userId);

        assertFalse(userService.bootstrapAdminRole("bootstrap-idempotent@koala.test"));

        assertEquals(1, rowCount(userId, "ROLE_ADMIN"));
        assertEquals(firstGrantedAt, grantedAt(userId), "Ikinci calisma rol verme anini degistirdi.");
    }

    @Test
    void theBootstrapGrantLeavesNoGranterBehind() {
        UUID userId = persistWorker("bootstrap-granter@koala.test");

        userService.bootstrapAdminRole("bootstrap-granter@koala.test");

        assertNotNull(grantedAt(userId), "granted_at bos kaldi.");
        assertNull(jdbcTemplate.queryForObject(
                "select granted_by_id from authorities where user_id = ? and role = 'ROLE_ADMIN'",
                UUID.class, userId));
    }

    @Test
    void aMissingAccountIsReportedRatherThanCreated() {
        assertThrows(ResourceNotFoundException.class,
                () -> userService.bootstrapAdminRole("bootstrap-yok@koala.test"));
    }

    @Test
    void theRunnerSwallowsTheMissingAccountSoStartupSurvives() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEmail("bootstrap-yok@koala.test");

        assertDoesNotThrow(() -> new AdminBootstrapRunner(properties, userService).run(null));
    }

    private Object grantedAt(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select granted_at from authorities where user_id = ? and role = 'ROLE_ADMIN'",
                java.sql.Timestamp.class, userId);
    }

    private int rowCount(UUID userId, String role) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ? and role = ?", Integer.class, userId, role);
        return found == null ? 0 : found;
    }
}
