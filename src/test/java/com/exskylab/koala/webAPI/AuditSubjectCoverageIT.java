package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.helpers.AuditScope;
import com.exskylab.koala.core.helpers.AuditSubjectSource;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditSubjectCoverageIT extends AbstractIntegrationTest {

    private static final String ADMIN_PREFIX = "/admin";
    private static final String AUTHENTICATION_PREFIX = "/admin/auth/";
    private static final String PROBE = "probe";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record SubjectMapping(String verb, String pattern, HandlerMethod handler, boolean mutating) {

        String resourceType() {
            return AuditScope.resourceTypeOf(pattern);
        }

        boolean targetsARecord() {
            return pattern.indexOf('{') >= 0;
        }

        boolean readsTheRecordFrom(String variable) {
            return variable != null && PROBE.equals(AuditScope.resourceIdOf(pattern, Map.of(variable, PROBE)));
        }

        @Override
        public String toString() {
            return verb + " " + pattern + " (" + handler.getBeanType().getSimpleName() + "."
                    + handler.getMethod().getName() + ")";
        }
    }

    private List<SubjectMapping> subjectMappings() {
        List<SubjectMapping> mappings = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequiresPermission required = declared(entry.getValue());
            if (required == null) {
                continue;
            }
            Set<RequestMethod> methods = verbsOf(entry.getKey().getMethodsCondition().getMethods());
            for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(ADMIN_PREFIX) || pattern.startsWith(AUTHENTICATION_PREFIX)) {
                    continue;
                }
                for (RequestMethod method : methods) {
                    boolean mutating = AuditScope.isMutating(method.name(), required.value());
                    if (AuditScope.carriesASubject(mutating, AuditScope.sensitivityOf(required.value()))) {
                        mappings.add(new SubjectMapping(method.name(), pattern, entry.getValue(), mutating));
                    }
                }
            }
        }
        return mappings;
    }

    static Set<RequestMethod> verbsOf(Set<RequestMethod> declared) {
        return declared.isEmpty() ? EnumSet.allOf(RequestMethod.class) : declared;
    }

    private List<SubjectMapping> mappingsThatNeedASource() {
        return subjectMappings().stream()
                .filter(mapping -> mapping.mutating() || mapping.targetsARecord())
                .toList();
    }

    private static RequiresPermission declared(HandlerMethod handler) {
        RequiresPermission onMethod =
                AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresPermission.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresPermission.class);
    }

    @Test
    void thereAreStaffDecisionsAndSensitiveReadsToCover() {
        List<SubjectMapping> mappings = mappingsThatNeedASource();
        assertTrue(mappings.stream().filter(SubjectMapping::mutating).count() >= 20,
                "En az 20 personel yazma ucu bekleniyordu, tarama boşa koşuyor: " + mappings);
        assertTrue(mappings.stream().anyMatch(mapping -> !mapping.mutating()),
                "Kayda yönelik hassas okuma ucu bekleniyordu, tarama boşa koşuyor: " + mappings);
    }

    @Test
    void aMappingThatDeclaresNoHttpMethodIsScannedForEveryVerb() {
        assertEquals(EnumSet.allOf(RequestMethod.class), verbsOf(Set.of()),
                "Yöntemsiz bir @RequestMapping her fiille çağrılabilir; tarama onu yazma olarak da saymalı.");
        assertEquals(Set.of(RequestMethod.POST), verbsOf(Set.of(RequestMethod.POST)));
    }

    @Test
    void everySubjectBearingStaffEndpointEitherResolvesItsSubjectOrSaysWhyItHasNone() {
        List<String> failures = new ArrayList<>();

        for (SubjectMapping mapping : mappingsThatNeedASource()) {
            Optional<AuditSubjectSource> source = AuditSubjectSource.of(mapping.resourceType());
            if (source.isEmpty()) {
                failures.add(mapping + " → kaynak türü '" + mapping.resourceType() + "' AuditSubjectSource'ta yok. "
                        + "Kararın ya da hassas okumanın konusu (USER|COMPANY) nasıl çözülüyor ya da neden yok, "
                        + "orada tanımlanmalı.");
                continue;
            }
            AuditSubjectSource declared = source.get();
            if (!declared.resolvesFromThePath()) {
                continue;
            }
            if (!mapping.targetsARecord()) {
                if (mapping.mutating()) {
                    failures.add(mapping + " → " + declared + " konuyu '{" + declared.getVariable()
                            + "}' yol değişkeninden çözer ama bu yazma ucunun yol değişkeni yok.");
                }
                continue;
            }
            if (!mapping.readsTheRecordFrom(declared.getVariable())) {
                failures.add(mapping + " → " + declared + " konuyu '{" + declared.getVariable()
                        + "}' yol değişkeninden çözer ama AuditScope.resourceIdOf bu uçta o değişkeni okumuyor; "
                        + "denetim satırının resourceId'si o kaydın kimliği olmaz.");
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void theCoverageCheckUsesTheSameParserAsTheRecorder() {
        assertTrue(new SubjectMapping("POST", "/admin/users/{userId}/roles", null, true)
                .readsTheRecordFrom("userId"));
        assertTrue(new SubjectMapping("POST", "/admin/users/{userId:[0-9a-f-]+}/roles", null, true)
                .readsTheRecordFrom("userId"));
        assertTrue(!new SubjectMapping("DELETE", "/admin/users/{userId}/roles/{role}", null, true)
                .readsTheRecordFrom("role"));
    }

    @Test
    void everySourceThatDoesNotReadThePathCarriesAWrittenReason() {
        List<String> silent = new ArrayList<>();
        for (AuditSubjectSource source : AuditSubjectSource.values()) {
            boolean hasReason = source.getReason() != null && !source.getReason().isBlank();
            boolean hasVariable = source.getVariable() != null && !source.getVariable().isBlank();
            if (source.resolvesFromThePath() == hasReason || source.resolvesFromThePath() != hasVariable) {
                silent.add(source.name());
            }
        }
        assertTrue(silent.isEmpty(),
                "Yoldan çözen kaynak bir yol değişkeni taşır ve gerekçe taşımaz; servisten damgalanan ya da "
                        + "konusuz kalan kaynak yol değişkeni taşımaz, gerekçe taşımak zorunda: " + silent);
    }

    @Test
    void everyDeclaredSourceStillGuardsAStaffEndpoint() {
        Set<AuditSubjectSource> unused = EnumSet.allOf(AuditSubjectSource.class);
        for (SubjectMapping mapping : mappingsThatNeedASource()) {
            AuditSubjectSource.of(mapping.resourceType()).ifPresent(unused::remove);
        }
        assertTrue(unused.isEmpty(), "Artık hiçbir personel ucuna karşılık gelmeyen kaynak: " + unused);
    }
}
