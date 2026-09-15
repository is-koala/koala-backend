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
    private static final Path IDENTITY_REVIEW_PERMISSION_CHANGELOG =
            Path.of("src/main/resources/db/changelog/2026/09/11-04-identity-document-review.xml");
    private static final Path NOTES_AND_FLAGS_PERMISSION_CHANGELOG =
            Path.of("src/main/resources/db/changelog/2026/09/15-05-staff-notes-and-flags.xml");

    private static final Pattern PAIR = Pattern.compile("\\('([A-Za-z_.]+)',\\s*'([A-Za-z_.]+)'\\)");

    private static final String TRUST = "TRUST";
    private static final String FINANCE = "FINANCE";
    private static final String OWNER = "OWNER";
    private static final String NEITHER = "NEITHER";

    private static final Map<String, String> DELIBERATE_BRANCH_OF = Map.ofEntries(
            Map.entry("CATALOG_EDITOR", NEITHER),
            Map.entry("SUPPORT_AGENT", NEITHER),
            Map.entry("AUDITOR", NEITHER),
            Map.entry("COMPANY_APPROVER", NEITHER),
            Map.entry("MODERATOR", TRUST),
            Map.entry("DISPUTE_HANDLER", TRUST),
            Map.entry("TRUST_LEAD", TRUST),
            Map.entry("FINANCE_ANALYST", FINANCE),
            Map.entry("FINANCE_OFFICER", FINANCE),
            Map.entry("OWNER", OWNER));

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
    void trustLeadReceivesTheDedicatedIdentityReviewPermission() throws IOException {
        String changelog = Files.readString(IDENTITY_REVIEW_PERMISSION_CHANGELOG);

        assertEquals(StaffPermission.IDENTITY_REVIEW,
                StaffPermission.fromCode("identity.review").orElseThrow());
        assertTrue(changelog.contains("role.code = 'TRUST_LEAD'"));
        assertTrue(changelog.contains("'identity.review'"));
    }

    @Test
    void supportAgentsWriteNotesAndOnlyTheTrustLeadManagesFlags() throws IOException {
        String changelog = Files.readString(NOTES_AND_FLAGS_PERMISSION_CHANGELOG);
        List<String[]> grants = pairs(changelog.substring(
                changelog.indexOf("INSERT INTO staff_role_permissions"),
                changelog.indexOf(") AS grant_row")));

        assertEquals(StaffPermission.NOTE_READ, StaffPermission.fromCode("note.read").orElseThrow());
        assertEquals(StaffPermission.NOTE_WRITE, StaffPermission.fromCode("note.write").orElseThrow());
        assertEquals(StaffPermission.FLAG_MANAGE, StaffPermission.fromCode("flag.manage").orElseThrow());
        assertEquals(List.of("SUPPORT_AGENT|note.read", "SUPPORT_AGENT|note.write", "TRUST_LEAD|flag.manage"),
                grants.stream().map(grant -> grant[0] + "|" + grant[1]).toList(),
                "not izinleri destek görevlisine, işaret yönetimi yalnız güven liderine tohumlanır");
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

    private static Set<String> seededRoleCodes() throws IOException {
        Set<String> seededRoles = new LinkedHashSet<>();
        Matcher role = Pattern.compile("'([A-Z_]+)',\\s+'").matcher(block("INSERT INTO staff_roles", "ON CONFLICT (code)"));
        while (role.find()) {
            seededRoles.add(role.group(1));
        }
        return seededRoles;
    }

    private static String runtimeBranchOf(String roleCode) {
        if (StaffSeparationOfDuties.isOwnerExemption(roleCode)) {
            return OWNER;
        }
        boolean trust = StaffSeparationOfDuties.isTrustBranch(roleCode);
        boolean finance = StaffSeparationOfDuties.isFinanceBranch(roleCode);

        if (trust && finance) {
            return "TRUST+FINANCE";
        }
        return trust ? TRUST : finance ? FINANCE : NEITHER;
    }

    @Test
    void everySeededRoleIsDeliberatelyPlacedInABranchOrOutsideBoth() throws IOException {
        Set<String> seededRoles = seededRoleCodes();

        List<String> unclassified = seededRoles.stream()
                .filter(role -> !DELIBERATE_BRANCH_OF.containsKey(role))
                .sorted()
                .toList();
        assertTrue(unclassified.isEmpty(),
                "Seed'e sınıflandırılmamış rol eklendi: her rol trust/finans dalına ya da bilinçli olarak hiçbirine "
                        + "atanmalı, yoksa görev ayrılığı kuralı ona sessizce uygulanmaz: " + unclassified);

        List<String> vanished = DELIBERATE_BRANCH_OF.keySet().stream()
                .filter(role -> !seededRoles.contains(role))
                .sorted()
                .toList();
        assertTrue(vanished.isEmpty(), "Seed'den kalkmış rol hâlâ sınıflandırma listesinde: " + vanished);

        List<String> drifted = seededRoles.stream()
                .filter(role -> !DELIBERATE_BRANCH_OF.get(role).equals(runtimeBranchOf(role)))
                .map(role -> role + " (burada " + DELIBERATE_BRANCH_OF.get(role)
                        + ", çalışma zamanında " + runtimeBranchOf(role) + ")")
                .sorted()
                .toList();
        assertTrue(drifted.isEmpty(),
                "Çalışma zamanı dal tanımı ile burada yazılı sınıflandırma ayrıştı: " + drifted);

        assertTrue(DELIBERATE_BRANCH_OF.containsValue(TRUST) && DELIBERATE_BRANCH_OF.containsValue(FINANCE)
                        && DELIBERATE_BRANCH_OF.containsValue(OWNER),
                "Kuralın uygulandığı bir seed bekleniyor: " + DELIBERATE_BRANCH_OF);
    }
}
