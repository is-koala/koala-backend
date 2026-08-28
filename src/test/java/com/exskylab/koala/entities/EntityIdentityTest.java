package com.exskylab.koala.entities;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIdentityTest {

    @Test
    void twoLoadsOfTheSameRowAreEqual() {
        UUID id = UUID.randomUUID();

        assertEquals(userWith(id), userWith(id));
        assertEquals(userWith(id).hashCode(), userWith(id).hashCode());
    }

    @Test
    void differentRowsAreNotEqual() {
        assertNotEquals(userWith(UUID.randomUUID()), userWith(UUID.randomUUID()));
    }

    @Test
    void unsavedEntitiesAreOnlyEqualToThemselves() {
        User first = userWith(null);
        User second = userWith(null);

        assertEquals(first, first);
        assertNotEquals(first, second);
    }

    @Test
    void differentTypesWithTheSameIdAreNotEqual() {
        UUID id = UUID.randomUUID();

        Company company = new Company();
        company.setId(id);

        assertNotEquals(userWith(id), company);
    }

    @Test
    void aSetDeduplicatesTheSameRow() {
        UUID id = UUID.randomUUID();
        Set<User> users = new HashSet<>();

        users.add(userWith(id));
        users.add(userWith(id));
        users.add(userWith(UUID.randomUUID()));

        assertEquals(2, users.size());
    }

    @Test
    void aReloadedEntityIsStillFoundInACollection() {
        UUID id = UUID.randomUUID();
        Set<User> users = new HashSet<>();
        users.add(userWith(id));

        assertTrue(users.contains(userWith(id)));
    }

    private User userWith(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
