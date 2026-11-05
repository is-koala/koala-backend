package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffAuditContextTest {

    @BeforeEach
    void open() {
        StaffAuditContext.open();
    }

    @AfterEach
    void close() {
        StaffAuditContext.close();
    }

    @Test
    void reclassifyingAFinancialReadAsAPersonalDataReadKeepsTheFinancialRetention() {
        pend(StaffPermission.PAYMENT_READ);

        StaffAuditContext.reclassify(StaffPermission.USER_READ_PII, StaffPermission.USER_READ_PII.getCode(), true);

        StaffAuditRecord record = StaffAuditContext.current().pending();
        assertEquals("user.read_pii", record.permission());
        assertEquals("user.read_pii", record.action());
        assertEquals(PermissionSensitivity.FINANCIAL, record.sensitivity());
    }

    @Test
    void reclassifyingRaisesTheSensitivityWhenTheNewPermissionIsMoreSensitive() {
        pend(StaffPermission.USER_READ);

        StaffAuditContext.reclassify(StaffPermission.USER_READ_PII, StaffPermission.USER_READ_PII.getCode(), true);

        assertEquals(PermissionSensitivity.PERSONAL_DATA, StaffAuditContext.current().pending().sensitivity());
    }

    private static void pend(StaffPermission permission) {
        StaffAuditContext.pend(new StaffAuditRecord(UUID.randomUUID(), "Personel", permission.getCode(),
                permission.getCode(), "job-assignments", UUID.randomUUID().toString(), true,
                permission.isMutating(), permission.getSensitivity(), "127.0.0.1", "test", AuditSubjects.NONE));
    }
}
