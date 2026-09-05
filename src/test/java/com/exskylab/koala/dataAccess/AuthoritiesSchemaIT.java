package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthoritiesSchemaIT extends AbstractIntegrationTest {

    private static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDao userDao;

    private UUID persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        return userDao.saveAndFlush(user).getId();
    }

    @Test
    void thePrimaryKeyIsTheUserAndRolePair() {
        List<String> keyColumns = jdbcTemplate.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                where tc.table_schema = 'public'
                  and tc.table_name = 'authorities'
                  and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.ordinal_position
                """, String.class);

        assertEquals(List.of("user_id", "role"), keyColumns,
                "authorities tablosunun bilesik birincil anahtari yok.");
    }

    @Test
    void theUserAndRolePairCarriesANamedUniquenessConstraint() {
        List<String> keyColumns = jdbcTemplate.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                where tc.table_schema = 'public'
                  and tc.constraint_name = 'uk_authorities_user_role'
                order by kcu.ordinal_position
                """, String.class);

        assertEquals(List.of("user_id", "role"), keyColumns,
                "uk_authorities_user_role kisiti yok.");
    }

    @Test
    void theSameRoleCannotBeGrantedTwice() {
        UUID userId = persistUser("authorities-duplicate@example.invalid");

        jdbcTemplate.update("insert into authorities (user_id, role) values (?, 'ROLE_USER')", userId);

        assertThrows(DuplicateKeyException.class,
                () -> jdbcTemplate.update("insert into authorities (user_id, role) values (?, 'ROLE_USER')", userId),
                "Ayni (user_id, role) cifti ikinci kez yazilabildi.");
    }

    @Test
    void grantProvenanceColumnsAreNullableAndTimeZoneAware() {
        Map<String, Object> grantedAt = jdbcTemplate.queryForMap("""
                select data_type, is_nullable
                from information_schema.columns
                where table_schema = 'public' and table_name = 'authorities' and column_name = 'granted_at'
                """);
        Map<String, Object> grantedBy = jdbcTemplate.queryForMap("""
                select data_type, is_nullable
                from information_schema.columns
                where table_schema = 'public' and table_name = 'authorities' and column_name = 'granted_by_id'
                """);

        assertEquals("timestamp with time zone", grantedAt.get("data_type"));
        assertEquals("YES", grantedAt.get("is_nullable"));
        assertEquals("uuid", grantedBy.get("data_type"));
        assertEquals("YES", grantedBy.get("is_nullable"));
    }

    @Test
    void theGrantingUserIsAForeignKeyIntoUsers() {
        List<String> targets = jdbcTemplate.queryForList("""
                select ccu.table_name || '.' || ccu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
                where tc.table_schema = 'public'
                  and tc.table_name = 'authorities'
                  and tc.constraint_type = 'FOREIGN KEY'
                  and kcu.column_name = 'granted_by_id'
                """, String.class);

        assertEquals(List.of("users.id"), targets,
                "granted_by_id users(id) tablosuna bagli degil.");
    }

    @Test
    void theSystemUsersSeededAdminGrantWasBackfilledWithATimestamp() {
        Integer backfilled = jdbcTemplate.queryForObject("""
                select count(*) from authorities
                where user_id = ?::uuid and role = 'ROLE_ADMIN' and granted_at is not null
                """, Integer.class, SYSTEM_USER_ID);

        assertEquals(1, backfilled, "Mevcut rol satirlarinin granted_at degeri doldurulmamis.");
    }

    @Test
    void theSeededAdminGrantHasNoGranter() {
        Integer withoutGranter = jdbcTemplate.queryForObject("""
                select count(*) from authorities
                where user_id = ?::uuid and role = 'ROLE_ADMIN' and granted_by_id is null
                """, Integer.class, SYSTEM_USER_ID);

        assertEquals(1, withoutGranter, "Sistem kullanicisinin rolunu veren biri yok, null kalmaliydi.");
    }
}
