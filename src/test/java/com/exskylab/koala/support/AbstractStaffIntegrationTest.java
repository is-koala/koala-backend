package com.exskylab.koala.support;

import com.exskylab.koala.core.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

public abstract class AbstractStaffIntegrationTest extends AbstractIntegrationTest {

    public static final String STAFF_PASSWORD = "Test-Personel-2026";

    @Autowired
    private JdbcTemplate staffJdbc;

    @Autowired
    private JwtService staffJwtService;

    @Autowired
    private PasswordEncoder staffPasswordEncoder;

    protected UUID createStaffMember(String email, String displayName, String... roleCodes) {
        UUID id = UUID.randomUUID();

        staffJdbc.update("""
                insert into staff_members (id, email, password_hash, display_name, is_active,
                                           must_change_password, failed_login_attempts, token_version, created_at)
                values (?, ?, ?, ?, true, false, 0, 0, now())
                """, id, email, staffPasswordEncoder.encode(STAFF_PASSWORD), displayName);

        for (String roleCode : roleCodes) {
            staffJdbc.update("""
                    insert into staff_member_roles (staff_member_id, staff_role_id, created_at)
                    select ?, r.id, now() from staff_roles r where r.code = ?
                    """, id, roleCode);
        }

        return id;
    }

    protected String staffAccessToken(UUID staffId) {
        Integer version = staffJdbc.queryForObject(
                "select token_version from staff_members where id = ?", Integer.class, staffId);

        return staffJwtService.generateStaffToken(staffId, version == null ? 0 : version,
                JwtService.STAFF_ACCESS_AUDIENCE, Duration.ofMinutes(15));
    }

    protected String staffTokenFor(String email, String displayName, String... roleCodes) {
        return staffAccessToken(createStaffMember(email, displayName, roleCodes));
    }
}
