package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrincipalTest {

    @Test
    void theprincipalNeverCarriesThePasswordHash() {
        User user = user();
        user.setPassword("$2a$10$cokgizlihash");

        assertNull(UserPrincipal.from(user).getPassword());
    }

    @Test
    void itCarriesOnlyIdEmailAndRoles() {
        User user = user();
        UserPrincipal principal = UserPrincipal.from(user);

        assertEquals(user.getId(), principal.id());
        assertEquals("ada@example.com", principal.getUsername());
        assertTrue(principal.hasRole(Role.ROLE_USER));
        assertFalse(principal.hasRole(Role.ROLE_ADMIN));
    }

    @Test
    void rolesAreCopiedSoLaterEntityChangesDoNotLeakIn() {
        User user = user();
        UserPrincipal principal = UserPrincipal.from(user);

        user.setAuthorities(Set.of(Role.ROLE_ADMIN));

        assertFalse(principal.hasRole(Role.ROLE_ADMIN),
                "Principal olusturulduktan sonra entity'deki rol degisikligi yansimamali.");
    }

    @Test
    void theRoleSetIsImmutable() {
        UserPrincipal principal = UserPrincipal.from(user());

        assertThrows(UnsupportedOperationException.class, () -> principal.roles().add(Role.ROLE_ADMIN));
    }

    @Test
    void aUserWithoutRolesProducesAnEmptySetNotNull() {
        User user = user();
        user.setAuthorities(null);

        assertTrue(UserPrincipal.from(user).getAuthorities().isEmpty());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ada@example.com");
        user.setAuthorities(Set.of(Role.ROLE_USER));
        return user;
    }
}
