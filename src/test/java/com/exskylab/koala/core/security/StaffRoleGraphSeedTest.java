package com.exskylab.koala.core.security;

import com.exskylab.koala.core.helpers.StaffSeparationOfDuties;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffRoleGraphSeedTest {

    private static final Path CHANGELOG =
            Path.of("src/main/resources/db/changelog/2026/09/06-20-staff-authorization.xml");
    private static final Path ACCOUNT_DELETION_PERMISSION_CHANGELOG =
            Path.of("src/main/resources/db/changelog/2026/09/06-21-account-deletion-staff-permission.xml");

    private static final Pattern PAIR = Pattern.compile("\\('([A-Za-z_.]+)',\\s*'([A-Za-z_.]+)'\\)");

    private static String block(String from, String to) throws IOException {
        String changelog = Files.readString(CHANGELOG);
        int start = changelog.indexOf(from);
        int end = changelog.indexOf(to, start);
        assertTrue(start >= 0 && end > start, from + " bloğu changelog'da bulunamadı");
        return changelog.substring(start, end);
    }

    private static List<String[]> pairs(String block) {
        List<String[]> rows = new ArrayList<>();
        Matcher pair = PAIR.matcher(block);
        while (pair.find()) {
            rows.add(new String[]{pair.group(1), pair.group(2)});
        }
        return rows;
    }

    private static List<String[]> grants() throws IOException {
        return pairs(block("INSERT INTO staff_role_permissions", ") AS grant_row"));
    }

    private static Map<String, Set<String>> includes() throws IOException {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (String[] edge : pairs(block("INSERT INTO staff_role_includes", ") AS edge"))) {
            edges.computeIfAbsent(edge[0], key -> new LinkedHashSet<>()).add(edge[1]);
        }
        return edges;
    }

    private static Set<String> closureOf(String role, Map<String, Set<String>> edges) {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(edges.getOrDefault(role, Set.of()));

        while (!pending.isEmpty()) {
            String next = pending.pop();
            if (reached.add(next)) {
                pending.addAll(edges.getOrDefault(next, Set.of()));
            }
        }
        return reached;
    }

    @Test
    void everySeededPermissionCodeIsARealConstant() throws IOException {
        List<String> unknown = grants().stream()
                .map(grant -> grant[1])
                .filter(code -> StaffPermission.fromCode(code).isEmpty())
                .distinct()
                .sorted()
                .toList();

        assertTrue(unknown.isEmpty(),
                "Rol grafiğinde StaffPermission karşılığı olmayan izin kodu: " + unknown);
    }

    @Test
    void theDocumentedPermissionNamesAreAllInTheCatalogue() {
        List<String> documented = List.of("category.manage", "user.read", "user.read_pii", "company.approve",
                "dispute.resolve", "payment.read", "finance.read", "payout.release", "payment.refund",
                "staff.manage", "role.manage", "audit.read");

        List<String> missing = documented.stream()
                .filter(code -> StaffPermission.fromCode(code).isEmpty())
                .toList();

        assertTrue(missing.isEmpty(), "ADR-0010'da adı geçen izin sabiti eksik: " + missing);
    }

    @Test
    void staffManagementAndRoleManagementStayTwoPermissions() {
        assertTrue(StaffPermission.fromCode("staff.manage").isPresent());
        assertTrue(StaffPermission.fromCode("role.manage").isPresent());
        assertEquals(StaffPermission.STAFF_MANAGE, StaffPermission.fromCode("staff.manage").orElseThrow());
        assertEquals(StaffPermission.ROLE_MANAGE, StaffPermission.fromCode("role.manage").orElseThrow());
    }

    @Test
    void trustLeadReceivesTheDedicatedAccountDeletionPermission() throws IOException {
        String changelog = Files.readString(ACCOUNT_DELETION_PERMISSION_CHANGELOG);

        assertEquals(StaffPermission.USER_DELETION_MANAGE,
                StaffPermission.fromCode("user.deletion.manage").orElseThrow());
        assertTrue(changelog.contains("role.code = 'TRUST_LEAD'"));
        assertTrue(changelog.contains("'user.deletion.manage'"));
    }

    @Test
    void everyPermissionCodeIsUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicated = new ArrayList<>();

        for (StaffPermission permission : StaffPermission.values()) {
            if (!seen.add(permission.getCode())) {
                duplicated.add(permission.getCode());
            }
        }

        assertTrue(duplicated.isEmpty(), "Aynı izin kodu iki sabitte: " + duplicated);
    }

    @Test
    void theKeycloakCompositeRolesAllExistInTheGraph() throws IOException {
        String roles = block("INSERT INTO staff_roles", "ON CONFLICT (code)");

        List<String> missing = List.of("CATALOG_EDITOR", "SUPPORT_AGENT", "FINANCE_ANALYST", "AUDITOR",
                        "MODERATOR", "DISPUTE_HANDLER", "COMPANY_APPROVER", "TRUST_LEAD",
                        "FINANCE_OFFICER", "OWNER").stream()
                .filter(role -> !roles.contains("'" + role + "'"))
                .toList();

        assertTrue(missing.isEmpty(), "keycloak-setup.md §3'teki rol grafikte yok: " + missing);
    }

    @Test
    void theIncludeGraphHasNoCycle() throws IOException {
        Map<String, Set<String>> edges = includes();

        List<String> cyclic = edges.keySet().stream()
                .filter(role -> closureOf(role, edges).contains(role))
                .sorted()
                .toList();

        assertTrue(cyclic.isEmpty(), "Rol grafiği DAG olmalı, döngü var: " + cyclic);
    }

    @Test
    void noRoleButOwnerSpansTheTrustAndFinanceBranches() throws IOException {
        Map<String, Set<String>> edges = includes();
        List<String> offenders = new ArrayList<>();

        for (String role : edges.keySet()) {
            if (StaffSeparationOfDuties.isOwnerExemption(role)) {
                continue;
            }
            Set<String> closure = new LinkedHashSet<>(closureOf(role, edges));
            closure.add(role);

            if (StaffSeparationOfDuties.spansBothBranches(closure)) {
                offenders.add(role + " → " + closure);
            }
        }

        assertTrue(offenders.isEmpty(), "Görev ayrılığı: OWNER dışında hiçbir rol her iki dalı kapsayamaz: " + offenders);
    }

    @Test
    void theSeedRuleAndTheRuntimeRuleReadTheSameBranchDefinition() throws IOException {
        Set<String> seededRoles = new LinkedHashSet<>();
        Matcher role = Pattern.compile("'([A-Z_]+)',\\s+'").matcher(block("INSERT INTO staff_roles", "ON CONFLICT (code)"));
        while (role.find()) {
            seededRoles.add(role.group(1));
        }

        assertTrue(seededRoles.stream().anyMatch(StaffSeparationOfDuties::isTrustBranch),
                "Kural tek yerde tanımlı olmalı; çalışma zamanı tanımı seed'deki trust dalını tanımıyor: " + seededRoles);
        assertTrue(seededRoles.stream().anyMatch(StaffSeparationOfDuties::isFinanceBranch),
                "Kural tek yerde tanımlı olmalı; çalışma zamanı tanımı seed'deki finans dalını tanımıyor: " + seededRoles);
        assertTrue(seededRoles.contains(StaffSeparationOfDuties.OWNER_ROLE_CODE));
    }
}
