package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BankAccountPrimaryInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from bank_accounts where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    @Test
    void postgresRefusesASecondLivePrimaryAccountForTheSameUser() {
        insertPrimary("TR180006200119000006672315");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertPrimary("TR960001000200300040005006"));
    }

    private void insertPrimary(String iban) {
        jdbcTemplate.update("insert into bank_accounts "
                        + "(id, user_id, iban, is_primary, is_active, verification_status, created_at, is_deleted) "
                        + "values (?, ?, ?, true, true, 'UNVERIFIED', now(), false)",
                UUID.randomUUID(), userId, iban);
    }
}
