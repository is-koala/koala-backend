package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffRoleAdministrationIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreTheSeededGraph() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'roles-%@iskoala.com'");
        jdbcTemplate.update("delete from staff_role_permissions where staff_role_id in "
                + "(select id from staff_roles where code like 'TEST_%')");
        jdbcTemplate.update("delete from staff_role_includes where parent_role_id in "
                + "(select id from staff_roles where code like 'TEST_%') "
                + "or child_role_id in (select id from staff_roles where code like 'TEST_%')");
        jdbcTemplate.update("delete from staff_roles where code like 'TEST_%'");
    }

    private UUID testRole(String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into staff_roles (id, code, name, created_at) values (?, ?, ?, now())",
                id, code, code);
        return id;
    }

    private void grant(UUID roleId, String permissionCode) {
        jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                + "values (?, ?, ?, now())", UUID.randomUUID(), roleId, permissionCode);
    }

    private void include(UUID parentRoleId, UUID childRoleId) {
        jdbcTemplate.update("insert into staff_role_includes (id, parent_role_id, child_role_id, created_at) "
                + "values (?, ?, ?, now())", UUID.randomUUID(), parentRoleId, childRoleId);
    }

    private Map<String, Object> auditRow(String action) {
        return jdbcTemplate.queryForMap("""
                select e.action, e.resource_id, d.before_value, d.after_value
                from admin_audit_entries e
                join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = ?
                """, action);
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("roles-" + UUID.randomUUID() + "@iskoala.com", "Rol Yöneticisi", roles);
    }

    private UUID roleId(String code) {
        return jdbcTemplate.queryForObject("select id from staff_roles where code = ?", UUID.class, code);
    }

    @Test
    void theRoleListCarriesEveryRoleWithItsOwnPermissionsAndItsDirectIncludes() throws Exception {
        mockMvc.perform(get("/admin/roles").header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.code == 'SUPPORT_AGENT')].permissions[*]",
                        containsInAnyOrder("user.read", "job.read")))
                .andExpect(jsonPath("$.data[?(@.code == 'TRUST_LEAD')].includes[*].code",
                        hasItem("MODERATOR")))
                .andExpect(jsonPath("$.data[?(@.code == 'OWNER')].id").exists());
    }

    @Test
    void theRoleDetailResolvesInheritedPermissionsTransitively() throws Exception {
        mockMvc.perform(get("/admin/roles/" + roleId("TRUST_LEAD"))
                        .header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("TRUST_LEAD"))
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder(
                        "user.read_pii", "user.deletion.manage", "identity.review")))
                .andExpect(jsonPath("$.data.includes[*].code",
                        containsInAnyOrder("DISPUTE_HANDLER", "MODERATOR")))
                .andExpect(jsonPath("$.data.effectiveRoles[*].code", containsInAnyOrder(
                        "DISPUTE_HANDLER", "MODERATOR", "SUPPORT_AGENT")))
                .andExpect(jsonPath("$.data.effectivePermissions", containsInAnyOrder(
                        "content.moderate", "dispute.resolve", "user.deletion.manage",
                        "user.read", "job.read", "user.read_pii", "identity.review")));
    }

    @Test
    void aPermissionGrantedThroughTheEndpointIsVisibleOnTheNextRead() throws Exception {
        UUID target = testRole("TEST_TARGET");
        String token = tokenFor("OWNER");

        mockMvc.perform(post("/admin/roles/" + target + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"category.manage\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles/" + target).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder("category.manage")))
                .andExpect(jsonPath("$.data.effectivePermissions", containsInAnyOrder("category.manage")));
    }

    @Test
    void aRevokedPermissionDisappearsFromTheRole() throws Exception {
        UUID target = testRole("TEST_TARGET");
        jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                + "values (?, ?, 'category.manage', now())", UUID.randomUUID(), target);
        String token = tokenFor("OWNER");

        mockMvc.perform(delete("/admin/roles/" + target + "/permissions/category.manage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles/" + target).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void anInclusionThatWouldCloseALoopIsRefusedWithAConflict() throws Exception {
        UUID parent = testRole("TEST_PARENT");
        UUID child = testRole("TEST_CHILD");
        String token = tokenFor("OWNER");

        mockMvc.perform(post("/admin/roles/" + parent + "/includes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includedRoleId\":\"" + child + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles/" + parent).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.includes[*].code", containsInAnyOrder("TEST_CHILD")));

        mockMvc.perform(post("/admin/roles/" + child + "/includes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includedRoleId\":\"" + parent + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_ROLE_GRAPH_WOULD_CYCLE"));
    }

    @Test
    void aRemovedInclusionStopsCarryingTheIncludedRolesPermissions() throws Exception {
        UUID parent = testRole("TEST_PARENT");
        UUID child = testRole("TEST_CHILD");
        jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                + "values (?, ?, 'category.manage', now())", UUID.randomUUID(), child);
        include(parent, child);
        String token = tokenFor("OWNER");

        mockMvc.perform(delete("/admin/roles/" + parent + "/includes/" + child)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles/" + parent).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.includes").isEmpty())
                .andExpect(jsonPath("$.data.effectivePermissions").isEmpty());
    }

    @Test
    void roleManagementCannotBeRevokedFromARoleTheActorReachesItself() throws Exception {
        UUID roleAdmin = testRole("TEST_ROLE_ADMIN");
        grant(roleAdmin, "role.manage");
        String token = tokenFor("TEST_ROLE_ADMIN");

        mockMvc.perform(delete("/admin/roles/" + roleAdmin + "/permissions/role.manage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_ROLE_MANAGE_SELF_LOCKOUT"));

        mockMvc.perform(get("/admin/roles/" + roleAdmin).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder("role.manage")));

        assertTrue(jdbcTemplate.queryForObject(
                        "select count(*) from staff_role_permissions where permission_code = 'role.manage'",
                        Integer.class) > 1,
                "Kural son sahipliği saymıyor: seed'deki OWNER da role.manage taşıyor ve istek yine reddedildi.");
    }

    @Test
    void roleManagementMayStillBeRevokedFromARoleTheActorDoesNotReach() throws Exception {
        UUID other = testRole("TEST_OTHER_ROLE_ADMIN");
        grant(other, "role.manage");
        String token = tokenFor("OWNER");

        mockMvc.perform(delete("/admin/roles/" + other + "/permissions/role.manage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles/" + other).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void staffWithoutRoleManagementIsRefusedByEveryEndpoint() throws Exception {
        UUID target = testRole("TEST_TARGET");
        UUID child = testRole("TEST_CHILD");
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(get("/admin/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/roles/" + target).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles/" + target + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"category.manage\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/roles/" + target + "/permissions/category.manage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles/" + target + "/includes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includedRoleId\":\"" + child + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/roles/" + target + "/includes/" + child)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotGiveARoleAPermissionItDoesNotHoldItself() throws Exception {
        UUID roleAdmin = testRole("TEST_ROLE_ADMIN");
        grant(roleAdmin, "role.manage");
        UUID target = testRole("TEST_TARGET");

        mockMvc.perform(post("/admin/roles/" + target + "/permissions")
                        .header("Authorization", "Bearer " + tokenFor("TEST_ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"payout.release\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STAFF_PERMISSION_NOT_GRANTABLE"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from staff_role_permissions where staff_role_id = ?", Integer.class, target));
    }

    @Test
    void thePermissionCatalogNamesEveryGrantableCodeWithItsMetadata() throws Exception {
        mockMvc.perform(get("/admin/permissions").header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'payout.release')].mutating").value(true))
                .andExpect(jsonPath("$.data[?(@.code == 'payout.release')].sensitivity").value("FINANCIAL"))
                .andExpect(jsonPath("$.data[?(@.code == 'user.read')].mutating").value(false))
                .andExpect(jsonPath("$.data[?(@.code == 'user.read')].sensitivity").value("NORMAL"));
    }

    @Test
    void aRevocationRecordsWhichPermissionLeftTheRole() throws Exception {
        UUID target = testRole("TEST_TARGET");
        grant(target, "category.manage");

        mockMvc.perform(delete("/admin/roles/" + target + "/permissions/category.manage")
                        .header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isOk());

        Map<String, Object> row = auditRow("staff.role.permission.revoke");

        assertEquals(target.toString(), row.get("resource_id"));
        assertTrue(String.valueOf(row.get("before_value")).contains("category.manage"),
                "Kaldırılan izin kodu yalnızca yolda geçiyor; iz onu taşımazsa hangi izin gitti bilinmez: " + row);
        assertFalse(String.valueOf(row.get("after_value")).contains("category.manage"), row.toString());
    }

    @Test
    void anInclusionRemovalRecordsWhichRoleLeftTheGraph() throws Exception {
        UUID parent = testRole("TEST_PARENT");
        UUID child = testRole("TEST_CHILD");
        include(parent, child);

        mockMvc.perform(delete("/admin/roles/" + parent + "/includes/" + child)
                        .header("Authorization", "Bearer " + tokenFor("OWNER")))
                .andExpect(status().isOk());

        Map<String, Object> row = auditRow("staff.role.include.remove");

        assertEquals(parent.toString(), row.get("resource_id"));
        assertTrue(String.valueOf(row.get("before_value")).contains("TEST_CHILD"),
                "Kaldırılan rol yalnızca yolun ikinci değişkeninde; iz onu taşımazsa kenar sessizce gider: " + row);
        assertFalse(String.valueOf(row.get("after_value")).contains("TEST_CHILD"), row.toString());
    }

    @Test
    void aGrantRecordsWhichPermissionJoinedTheRole() throws Exception {
        UUID target = testRole("TEST_TARGET");

        mockMvc.perform(post("/admin/roles/" + target + "/permissions")
                        .header("Authorization", "Bearer " + tokenFor("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"category.manage\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = auditRow("staff.role.permission.grant");

        assertFalse(String.valueOf(row.get("before_value")).contains("category.manage"), row.toString());
        assertTrue(String.valueOf(row.get("after_value")).contains("category.manage"), row.toString());
    }

    @Test
    void anInclusionRecordsWhichRoleJoinedTheGraph() throws Exception {
        UUID parent = testRole("TEST_PARENT");
        UUID child = testRole("TEST_CHILD");

        mockMvc.perform(post("/admin/roles/" + parent + "/includes")
                        .header("Authorization", "Bearer " + tokenFor("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includedRoleId\":\"" + child + "\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = auditRow("staff.role.include.add");

        assertFalse(String.valueOf(row.get("before_value")).contains("TEST_CHILD"), row.toString());
        assertTrue(String.valueOf(row.get("after_value")).contains("TEST_CHILD"), row.toString());
    }
}
