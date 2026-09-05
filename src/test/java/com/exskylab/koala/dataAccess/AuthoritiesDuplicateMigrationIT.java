package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthoritiesDuplicateMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDao userDao;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @AfterEach
    void leaveTheSchemaMigrated() {
        if (primaryKeyColumns() != 2) {
            removeDuplicateGrants();
            applyChangeLog();
        }
    }

    @Test
    void theChangeLogSurvivesADatabaseThatAlreadyCarriesDuplicateGrants() {
        UUID userId = persistUser("authorities-migration@example.invalid");

        unapplyTheAuthoritiesChangeSets();

        grant(userId, "ROLE_USER");
        grant(userId, "ROLE_USER");
        grant(userId, "ROLE_ADMIN");
        grant(userId, "ROLE_ADMIN");
        grant(userId, "ROLE_ADMIN");

        assertDoesNotThrow(this::applyChangeLog,
                "Mukerrer satir tasiyan veritabaninda changelog patladi.");

        assertEquals(1, grantCount(userId, "ROLE_USER"), "Mukerrer ROLE_USER satiri temizlenmedi.");
        assertEquals(1, grantCount(userId, "ROLE_ADMIN"), "Mukerrer ROLE_ADMIN satiri temizlenmedi.");
        assertEquals(2, primaryKeyColumns(), "Bilesik birincil anahtar geri gelmedi.");

        Integer withoutTimestamp = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ? and granted_at is null",
                Integer.class, userId);
        assertEquals(0, withoutTimestamp, "Temizlikten sonra kalan satirlarin granted_at degeri bos.");
    }

    private UUID persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        return userDao.saveAndFlush(user).getId();
    }

    private void grant(UUID userId, String role) {
        jdbcTemplate.update("insert into authorities (user_id, role) values (?, ?)", userId, role);
    }

    private int grantCount(UUID userId, String role) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from authorities where user_id = ? and role = ?",
                Integer.class, userId, role);
        return found == null ? 0 : found;
    }

    private void unapplyTheAuthoritiesChangeSets() {
        jdbcTemplate.execute("alter table public.authorities drop constraint if exists pk_authorities");
        jdbcTemplate.execute("alter table public.authorities drop constraint if exists uk_authorities_user_role");
        jdbcTemplate.execute("alter table public.authorities drop constraint if exists fk_authorities_on_granted_by");
        jdbcTemplate.execute("alter table public.authorities drop column if exists granted_by_id");
        jdbcTemplate.execute("alter table public.authorities drop column if exists granted_at");
        jdbcTemplate.update("delete from databasechangelog where id like '2026-09-06-34-%'");
    }

    private void removeDuplicateGrants() {
        jdbcTemplate.execute("""
                delete from public.authorities a
                where a.ctid <> (select min(b.ctid) from public.authorities b
                                 where b.user_id = a.user_id and b.role = a.role)
                """);
    }

    private void applyChangeLog() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.xml");
        liquibase.setResourceLoader(resourceLoader);
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int primaryKeyColumns() {
        Integer found = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                where tc.table_schema = 'public'
                  and tc.table_name = 'authorities'
                  and tc.constraint_type = 'PRIMARY KEY'
                """, Integer.class);
        return found == null ? 0 : found;
    }
}
