package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.helpers.StaffSeparationOfDuties;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffSeparationOfDutiesIT extends AbstractStaffIntegrationTest {

    private static final String ERROR_CODE = "STAFF_SEPARATION_OF_DUTIES_VIOLATED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void restoreTheSeededGraph() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'sod-%@iskoala.com')");
        deleteStaffMembersLike("sod-%@iskoala.com");
        jdbcTemplate.update("delete from staff_role_includes where parent_role_id in "
                + "(select id from staff_roles where code like 'TEST_SOD%') "
                + "or child_role_id in (select id from staff_roles where code like 'TEST_SOD%')");
        jdbcTemplate.update("delete from staff_roles where code like 'TEST_SOD%'");
    }

    private UUID testRole(String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into staff_roles (id, code, name, created_at) values (?, ?, ?, now())",
                id, code, code);
        return id;
    }

    private UUID roleId(String code) {
        return jdbcTemplate.queryForObject("select id from staff_roles where code = ?", UUID.class, code);
    }

    private UUID staff(String... roleCodes) {
        return createStaffMember("sod-" + UUID.randomUUID() + "@iskoala.com", "Görev Ayrılığı", roleCodes);
    }

    private String ownerToken() {
        return staffAccessToken(staff("OWNER"));
    }

    private List<String> includedCodesOf(UUID parentRoleId) {
        return jdbcTemplate.queryForList("""
                select r.code from staff_role_includes i
                join staff_roles r on r.id = i.child_role_id
                where i.parent_role_id = ? order by r.code
                """, String.class, parentRoleId);
    }

    private List<String> roleCodesOf(UUID staffId) {
        return jdbcTemplate.queryForList("""
                select r.code from staff_member_roles m
                join staff_roles r on r.id = m.staff_role_id
                where m.staff_member_id = ? order by r.code
                """, String.class, staffId);
    }

    private ResultActions include(UUID parentRoleId, UUID childRoleId, String token) throws Exception {
        return mockMvc.perform(post("/admin/roles/" + parentRoleId + "/includes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"includedRoleId\":\"" + childRoleId + "\"}"));
    }

    private ResultActions grant(UUID staffId, String roleCode, String token) throws Exception {
        return mockMvc.perform(post("/admin/staff/" + staffId + "/roles/" + roleCode)
                .header("Authorization", "Bearer " + token));
    }

    private void insertInclusionWithoutTheEndpoint(UUID parentRoleId, UUID childRoleId) {
        jdbcTemplate.update("insert into staff_role_includes (id, parent_role_id, child_role_id, created_at) "
                + "values (?, ?, ?, now())", UUID.randomUUID(), parentRoleId, childRoleId);
    }

    private List<String> closureCodesOf(UUID roleId) {
        return jdbcTemplate.queryForList("""
                with recursive reachable(id) as (
                    select cast(? as uuid)
                    union
                    select i.child_role_id from staff_role_includes i join reachable x on i.parent_role_id = x.id
                )
                select r.code from staff_roles r join reachable v on v.id = r.id order by r.code
                """, String.class, roleId);
    }

    private List<String> reachedCodesOf(UUID staffId) {
        return jdbcTemplate.queryForList("""
                with recursive assigned(id) as (
                    select staff_role_id from staff_member_roles where staff_member_id = cast(? as uuid)
                ), reachable(id) as (
                    select id from assigned
                    union
                    select i.child_role_id from staff_role_includes i join reachable x on i.parent_role_id = x.id
                )
                select r.code from staff_roles r join reachable v on v.id = r.id order by r.code
                """, String.class, staffId);
    }

    @Test
    void anInclusionThatWouldSpanTheTrustAndFinanceBranchesIsRefused() throws Exception {
        UUID spanning = testRole("TEST_SOD_SPANNING");
        String token = ownerToken();

        include(spanning, roleId("TRUST_LEAD"), token).andExpect(status().isOk());

        include(spanning, roleId("FINANCE_OFFICER"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of("TRUST_LEAD"), includedCodesOf(spanning),
                "Reddedilen kapsama yazılmamalı");
    }

    @Test
    void anInclusionThatReachesTheOtherBranchTransitivelyIsAlsoRefused() throws Exception {
        UUID finance = testRole("TEST_SOD_FINANCE_WRAPPER");
        UUID spanning = testRole("TEST_SOD_TRANSITIVE");
        String token = ownerToken();

        include(finance, roleId("FINANCE_ANALYST"), token).andExpect(status().isOk());
        include(spanning, roleId("MODERATOR"), token).andExpect(status().isOk());

        include(spanning, finance, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of("MODERATOR"), includedCodesOf(spanning),
                "Finans dalı bir ara rol üzerinden de ulaşılıyor olsa kural işlemeli");
    }

    @Test
    void anInclusionIsRefusedWhenAnAncestorOfTheParentAlreadyReachesTheOtherBranch() throws Exception {
        UUID top = testRole("TEST_SOD_ANCESTOR_TOP");
        UUID mid = testRole("TEST_SOD_ANCESTOR_MID");
        String token = ownerToken();

        include(top, roleId("MODERATOR"), token).andExpect(status().isOk());
        include(top, mid, token).andExpect(status().isOk());

        include(mid, roleId("FINANCE_OFFICER"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of(), includedCodesOf(mid),
                "Kenarın ebeveyni temiz olsa da atası iki dalı birden kapsayacaksa kenar yazılmamalı");
        assertFalse(StaffSeparationOfDuties.spansBothBranches(closureCodesOf(top)),
                "Kural kenarın yalnız aşağısına değil, ebeveyne ulaşan her role bakmalı");
    }

    @Test
    void anInclusionIsRefusedWhenItWouldSpanTheBranchesForStaffAlreadyCarryingTheParent() throws Exception {
        UUID carrier = testRole("TEST_SOD_CARRIER");
        String token = ownerToken();
        UUID target = staff("MODERATOR");

        grant(target, "TEST_SOD_CARRIER", token).andExpect(status().isOk());

        include(carrier, roleId("FINANCE_OFFICER"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of(), includedCodesOf(carrier),
                "Rol grafiği tek başına temiz kalsa da üyelik iki dala birden ulaşacaksa kenar yazılmamalı");
        assertFalse(StaffSeparationOfDuties.spansBothBranches(reachedCodesOf(target)),
                "Personel, boşken verilen bir role sonradan asılan dal üzerinden iki dala ulaşamamalı");
    }

    @Test
    void anInclusionThatAlreadyExistsIsAcceptedAgain() throws Exception {
        UUID trustOnly = testRole("TEST_SOD_REPOST");
        String token = ownerToken();

        include(trustOnly, roleId("MODERATOR"), token).andExpect(status().isOk());
        include(trustOnly, roleId("MODERATOR"), token).andExpect(status().isOk());

        assertEquals(List.of("MODERATOR"), includedCodesOf(trustOnly));
    }

    @Test
    void anInclusionRowThatPredatesTheRuleIsStillIdempotent() throws Exception {
        UUID spanning = testRole("TEST_SOD_LEGACY");
        String token = ownerToken();

        insertInclusionWithoutTheEndpoint(spanning, roleId("MODERATOR"));
        insertInclusionWithoutTheEndpoint(spanning, roleId("FINANCE_OFFICER"));

        include(spanning, roleId("FINANCE_OFFICER"), token).andExpect(status().isOk());

        assertEquals(List.of("FINANCE_OFFICER", "MODERATOR"), includedCodesOf(spanning),
                "Kuraldan önce yazılmış aykırı bir satır no-op çağrıyı patlatmamalı");
    }

    @Test
    void twoRolesFromTheSameBranchStillCombine() throws Exception {
        UUID trustOnly = testRole("TEST_SOD_TRUST_ONLY");
        String token = ownerToken();

        include(trustOnly, roleId("MODERATOR"), token).andExpect(status().isOk());
        include(trustOnly, roleId("DISPUTE_HANDLER"), token).andExpect(status().isOk());

        assertEquals(List.of("DISPUTE_HANDLER", "MODERATOR"), includedCodesOf(trustOnly),
                "Kural yalnız iki dalı birleştiren yazımı kesmeli, aynı daldakini değil");
    }

    @Test
    void theOwnerRoleMayStillSpanBothBranches() throws Exception {
        UUID leaf = testRole("TEST_SOD_LEAF");
        UUID owner = roleId("OWNER");

        include(owner, leaf, ownerToken()).andExpect(status().isOk());

        assertTrue(includedCodesOf(owner).contains("TEST_SOD_LEAF"),
                "OWNER istisnası olmadan seed'deki OWNER her iki dalı kapsadığı için bu yazım da reddedilirdi");
    }

    @Test
    void aMembershipThatWouldSpanTheTrustAndFinanceBranchesIsRefused() throws Exception {
        UUID target = staff("TRUST_LEAD");

        grant(target, "FINANCE_OFFICER", ownerToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of("TRUST_LEAD"), roleCodesOf(target), "Reddedilen üyelik yazılmamalı");
    }

    @Test
    void aMembershipIsRefusedInEitherOrderTheRolesArrive() throws Exception {
        UUID target = staff("FINANCE_ANALYST");

        grant(target, "MODERATOR", ownerToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ERROR_CODE));

        assertEquals(List.of("FINANCE_ANALYST"), roleCodesOf(target));
    }

    @Test
    void aMembershipWithinASingleBranchIsStillGranted() throws Exception {
        UUID target = staff("MODERATOR");

        grant(target, "DISPUTE_HANDLER", ownerToken()).andExpect(status().isOk());

        assertEquals(List.of("DISPUTE_HANDLER", "MODERATOR"), roleCodesOf(target));
    }

    @Test
    void anOwnerMembershipMayStillSpanBothBranches() throws Exception {
        UUID target = staff();

        grant(target, "OWNER", ownerToken()).andExpect(status().isOk());

        assertEquals(List.of("OWNER"), roleCodesOf(target),
                "OWNER istisnası olmadan OWNER üyeliği hiç verilemezdi");
    }

    @Test
    void concurrentGraphAndMembershipWritesCannotTogetherSpanTheBranches() throws Exception {
        UUID carrier = testRole("TEST_SOD_CONCURRENT");
        UUID target = staff("MODERATOR");
        String token = ownerToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        HikariDataSource pool = (HikariDataSource) dataSource;
        try (Connection blocker = DriverManager.getConnection(
                pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
             ExecutorService requests = Executors.newFixedThreadPool(2)) {
            execute(blocker, "select pg_advisory_lock(hashtextextended('staff-separation-of-duties', 0))");
            Future<Integer> include = requests.submit(() -> {
                ready.countDown();
                start.await();
                return include(carrier, roleId("FINANCE_OFFICER"), token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> grant = requests.submit(() -> {
                ready.countDown();
                start.await();
                return grant(target, "TEST_SOD_CONCURRENT", token)
                        .andReturn().getResponse().getStatus();
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS), "İki istek de başlamalı");
            start.countDown();
            try {
                awaitBothRequestsOnTheSeparationLock(blocker, include, grant);
            } finally {
                execute(blocker, "select pg_advisory_unlock(hashtextextended('staff-separation-of-duties', 0))");
            }

            List<Integer> statuses = List.of(
                    include.get(10, TimeUnit.SECONDS),
                    grant.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses,
                    "Eşzamanlı yazımlardan yalnızca biri görev ayrılığı değişmezini geçebilmeli");
        }

        assertFalse(StaffSeparationOfDuties.spansBothBranches(closureCodesOf(carrier)));
        assertFalse(StaffSeparationOfDuties.spansBothBranches(reachedCodesOf(target)));
    }

    private static void awaitBothRequestsOnTheSeparationLock(
            Connection blocker, Future<Integer> include, Future<Integer> grant)
            throws InterruptedException, SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            assertFalse(include.isDone() || grant.isDone(),
                    "Her iki yazma yolu da ortak transaction kilidini almadan ilerlememeli");
            int waiting = queryForInt(blocker, """
                    select count(*) from pg_stat_activity
                    where wait_event = 'advisory'
                      and query like '%pg_advisory_xact_lock%'
                    """);
            if (waiting >= 2) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("İki yazma yolu ortak advisory kilitte beklemedi");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
