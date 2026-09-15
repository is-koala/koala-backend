package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.RunbookSql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffRecoveryRunbookIT extends AbstractStaffIntegrationTest {

    private static final String ASSIGNED_PASSWORD = "Runbook-Tek-Kullanimlik-2026";
    private static final String EMAIL_PATTERN = "recovery-%@iskoala.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void removeTestStaff() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like '" + EMAIL_PATTERN + "')");
        deleteStaffMembersLike(EMAIL_PATTERN);
    }

    @Test
    void theRunbookUpdateUnlocksTheLastOwnerAndForcesAPasswordChangeOnTheNextSignIn() throws Exception {
        String address = "recovery-" + UUID.randomUUID() + "@iskoala.com";
        UUID staffId = createStaffMember(address, "Son Sahip", "OWNER");
        String oldToken = staffAccessToken(staffId);
        jdbcTemplate.update("""
                update staff_members
                   set failed_login_attempts = 0,
                       locked_until = now() + interval '15 minutes',
                       token_version = 3
                 where id = ?
                """, staffId);

        String hash = passwordEncoder.encode(ASSIGNED_PASSWORD);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2y$"), hash);

        assertEquals(1, jdbcTemplate.update(unlockAndResetFromTheRunbook(), hash, staffId),
                "Runbook update'i tam bir satırı kurtarmalı.");

        Map<String, Object> recovered = jdbcTemplate.queryForMap(inspectFromTheRunbook(), staffId);
        assertEquals(Boolean.TRUE, recovered.get("must_change_password"));
        assertEquals(0, ((Number) recovered.get("failed_login_attempts")).intValue());
        assertNull(recovered.get("locked_until"));
        assertEquals(4, ((Number) recovered.get("token_version")).intValue());
        assertTrue(passwordEncoder.matches(ASSIGNED_PASSWORD, (String) recovered.get("password_hash")));

        mockMvc.perform(get("/admin/job-categories").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", address, "password", ASSIGNED_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());

        String assignedToken = staffAccessToken(staffId);
        mockMvc.perform(post("/admin/auth/password").header("Authorization", "Bearer " + assignedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ASSIGNED_PASSWORD,
                                "newPassword", "Runbook-Sonrasi-Parola-2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", address, "password", "Runbook-Sonrasi-Parola-2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void theRunbookUpdateNeverTouchesADifferentStaffMember() {
        UUID intended = createStaffMember("recovery-" + UUID.randomUUID() + "@iskoala.com", "Hedef", "OWNER");
        UUID bystander = createStaffMember("recovery-" + UUID.randomUUID() + "@iskoala.com", "Yan", "OWNER");
        jdbcTemplate.update("update staff_members set token_version = 1, locked_until = now() + interval '5 minutes' "
                + "where id in (?, ?)", intended, bystander);

        String hash = passwordEncoder.encode(ASSIGNED_PASSWORD);
        assertEquals(1, jdbcTemplate.update(unlockAndResetFromTheRunbook(), hash, intended));

        assertNotNull(jdbcTemplate.queryForObject(
                "select locked_until from staff_members where id = ?", java.sql.Timestamp.class, bystander));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select token_version from staff_members where id = ?", Integer.class, bystander).intValue());
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "select must_change_password from staff_members where id = ?", Boolean.class, bystander));
    }

    private static String unlockAndResetFromTheRunbook() {
        String block = RunbookSql.theOnlyBlock(RunbookSql.STAFF_RECOVERY,
                candidate -> candidate.startsWith("update staff_members")
                        && candidate.contains("must_change_password = true")
                        && candidate.contains("token_version = token_version + 1"),
                "kilidi açıp tek kullanımlık parola atayan update'i");
        assertTrue(block.contains("failed_login_attempts = 0"), block);
        assertTrue(block.contains("locked_until = null"), block);
        assertTrue(block.contains("password_hash = :bcrypt_hash"), block);
        assertTrue(block.contains("where id = :staff_id"), block);
        return block.replace(":bcrypt_hash", "?").replace(":staff_id", "?").replace(";", "");
    }

    private static String inspectFromTheRunbook() {
        String block = RunbookSql.theOnlyBlock(RunbookSql.STAFF_RECOVERY,
                candidate -> candidate.startsWith("select")
                        && candidate.contains("must_change_password")
                        && candidate.contains("password_hash")
                        && candidate.contains("where id = :staff_id"),
                "kurtarma sonrası kontrol sorgusunu");
        return block.replace(":staff_id", "?").replace(";", "");
    }
}
