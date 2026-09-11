package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionMappingIT extends AbstractIntegrationTest {

    private static final String ADMIN_PREFIX = "/admin";
    private static final String AUTHENTICATION_PREFIX = "/admin/auth/";
    private static final String IDENTITY_PATTERN = "/admin/me";

    private static final List<String> MONEY_MOVING_CALLS = List.of(
            "staffPayoutService.releaseToWorker(",
            "staffPayoutService.refundEmployer(",
            "payoutService.releaseToWorker(",
            "payoutService.refundEmployer(",
            "disputeService.resolveDispute(");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record AdminMapping(String verb, String pattern, HandlerMethod handler) {

        RequiresPermission declared() {
            RequiresPermission onMethod =
                    AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresPermission.class);
            return onMethod != null
                    ? onMethod
                    : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresPermission.class);
        }

        @Override
        public String toString() {
            return verb + " " + pattern + " (" + handler.getBeanType().getSimpleName() + "."
                    + handler.getMethod().getName() + ")";
        }
    }

    private List<AdminMapping> adminMappings() {
        List<AdminMapping> mappings = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            for (String pattern : patternsOf(entry.getKey())) {
                if (!pattern.startsWith(ADMIN_PREFIX) || pattern.startsWith(AUTHENTICATION_PREFIX)) {
                    continue;
                }
                for (String verb : verbsOf(entry.getKey())) {
                    mappings.add(new AdminMapping(verb, pattern, entry.getValue()));
                }
            }
        }
        return mappings;
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition() == null ? Set.of() : info.getPatternsCondition().getPatterns();
    }

    private static Set<String> verbsOf(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return new LinkedHashSet<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        }
        return methods.stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    void thereIsSomethingToGuard() {
        assertFalse(adminMappings().isEmpty(), "/admin/** altında en az bir mapping bekleniyordu");
    }

    @Test
    void everyAdminMappingDeclaresThePermissionItNeeds() {
        List<String> undeclared = adminMappings().stream()
                .filter(mapping -> mapping.declared() == null)
                .map(AdminMapping::toString)
                .sorted()
                .toList();

        assertTrue(undeclared.isEmpty(),
                "@RequiresPermission taşımayan yönetim ucu — anotasyon yoksa yetki kapısı yoktur: " + undeclared);
    }

    @Test
    void everyNamedPermissionIsARealConstant() {
        List<String> unknown = new ArrayList<>();

        for (AdminMapping mapping : adminMappings()) {
            RequiresPermission declared = mapping.declared();
            if (declared == null) {
                continue;
            }
            for (StaffPermission permission : declared.value()) {
                if (StaffPermission.fromCode(permission.getCode()).orElse(null) != permission) {
                    unknown.add(mapping + " → " + permission.getCode());
                }
            }
        }

        assertTrue(unknown.isEmpty(), "Gerçek bir StaffPermission sabitine karşılık gelmeyen izin: " + unknown);
    }

    @Test
    void everyWritingMappingCarriesAnAuditActionName() {
        List<String> silent = new ArrayList<>();

        for (AdminMapping mapping : adminMappings()) {
            RequiresPermission declared = mapping.declared();
            if (declared == null || "GET".equals(mapping.verb())) {
                continue;
            }
            if (declared.action().isBlank()) {
                silent.add(mapping.toString());
            }
        }

        assertTrue(silent.isEmpty(), "Denetim eylem adı taşımayan yazma ucu: " + silent);
    }

    @Test
    void everyWritingMappingIsGuardedByAMutatingPermission() {
        List<String> weak = new ArrayList<>();

        for (AdminMapping mapping : adminMappings()) {
            RequiresPermission declared = mapping.declared();
            if (declared == null || "GET".equals(mapping.verb())) {
                continue;
            }
            if (declared.value().length == 0
                    || Arrays.stream(declared.value()).anyMatch(permission -> !permission.isMutating())) {
                weak.add(mapping + " → " + Arrays.stream(declared.value()).map(StaffPermission::getCode).toList());
            }
        }

        assertTrue(weak.isEmpty(), "Yazma ucu okuma izniyle korunamaz: " + weak);
    }

    private static String handlerBody(HandlerMethod handler) throws IOException {
        Path file = Paths.get("src/main/java/" + handler.getBeanType().getName().replace('.', '/') + ".java");
        if (!Files.exists(file)) {
            return "";
        }

        String source = Files.readString(file);
        int signature = source.indexOf(" " + handler.getMethod().getName() + "(");
        int open = signature < 0 ? -1 : source.indexOf('{', signature);
        if (open < 0) {
            return "";
        }

        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, index + 1).replaceAll("\\s+", "");
                }
            }
        }
        return source.substring(open).replaceAll("\\s+", "");
    }

    private static boolean movesMoney(String body) {
        return MONEY_MOVING_CALLS.stream().anyMatch(body::contains);
    }

    private List<AdminMapping> moneyMovingMappings() throws IOException {
        List<AdminMapping> moving = new ArrayList<>();
        for (AdminMapping mapping : adminMappings()) {
            if (movesMoney(handlerBody(mapping.handler()))) {
                moving.add(mapping);
            }
        }
        return moving;
    }

    @Test
    void theFinancialInvariantCoversEveryMoneyMovingSurface() throws IOException {
        List<String> covered = moneyMovingMappings().stream().map(AdminMapping::toString).sorted().toList();

        assertTrue(covered.size() >= 3,
                "para hareket ettiren yönetim ucu bulunamadı; değişmez boşa koşuyor demektir: " + covered);
    }

    @Test
    void noMoneyMovingMappingIsGuardedOnlyByANonFinancialPermission() throws IOException {
        List<String> weak = new ArrayList<>();

        for (AdminMapping mapping : moneyMovingMappings()) {
            RequiresPermission declared = mapping.declared();

            boolean namesAFinancialPermission = declared != null && Arrays.stream(declared.value())
                    .anyMatch(permission -> permission.getSensitivity() == PermissionSensitivity.FINANCIAL);

            boolean handsTheActorToTheFacade =
                    Arrays.asList(mapping.handler().getMethod().getParameterTypes()).contains(StaffPrincipal.class)
                            && handlerBody(mapping.handler()).contains("(actor,");

            if (!namesAFinancialPermission && !handsTheActorToTheFacade) {
                weak.add(mapping + " → " + (declared == null
                        ? "izinsiz"
                        : Arrays.stream(declared.value()).map(StaffPermission::getCode).toList()));
            }
        }

        assertTrue(weak.isEmpty(),
                "Para hareketi FINANCIAL olmayan bir izinle korunamaz: uç ya kendisi finansal bir izin "
                        + "adlandırmalı ya da personel aktörünü izni denetleyen facade'a geçirmeli. " + weak);
    }

    @Test
    void everyPermissionConstantIsBoundToAtLeastOneAdminMapping() {
        Set<StaffPermission> bound = new LinkedHashSet<>();
        for (AdminMapping mapping : adminMappings()) {
            RequiresPermission declared = mapping.declared();
            if (declared == null) {
                continue;
            }
            bound.addAll(Arrays.asList(declared.value()));
        }

        List<StaffPermission> unused = Arrays.stream(StaffPermission.values())
                .filter(permission -> !bound.contains(permission))
                .toList();

        assertTrue(unused.isEmpty(),
                "Tohumda olup hiçbir @RequiresPermission'a bağlanmamış izin — panel izni kör kalır: "
                        + unused);
    }

    @Test
    void onlyTheIdentityEndpointMayNameNoPermission() {
        List<String> open = adminMappings().stream()
                .filter(mapping -> mapping.declared() != null && mapping.declared().value().length == 0)
                .filter(mapping -> !IDENTITY_PATTERN.equals(mapping.pattern()))
                .map(AdminMapping::toString)
                .sorted()
                .toList();

        assertTrue(open.isEmpty(),
                "İzin adı vermeyen anotasyon yalnız " + IDENTITY_PATTERN + " için geçerlidir: " + open);
    }
}
