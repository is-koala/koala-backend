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

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditSubjectCoverageIT extends AbstractIntegrationTest {

    private static final String ADMIN_PREFIX = "/admin";
    private static final String AUTHENTICATION_PREFIX = "/admin/auth/";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record WriteMapping(String verb, String pattern, HandlerMethod handler) {

        String resourceType() {
            return AuditScope.resourceTypeOf(pattern);
        }

        Optional<String> firstVariable() {
            int open = pattern.indexOf('{');
            int close = open < 0 ? -1 : pattern.indexOf('}', open);
            if (close < 0) {
                return Optional.empty();
            }
            String name = pattern.substring(open + 1, close);
            int constraint = name.indexOf(':');
            return Optional.of(constraint < 0 ? name : name.substring(0, constraint));
        }

        @Override
        public String toString() {
            return verb + " " + pattern + " (" + handler.getBeanType().getSimpleName() + "."
                    + handler.getMethod().getName() + ")";
        }
    }

    private List<WriteMapping> writeMappings() {
        List<WriteMapping> mappings = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (declared(entry.getValue()) == null) {
                continue;
            }
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(ADMIN_PREFIX) || pattern.startsWith(AUTHENTICATION_PREFIX)) {
                    continue;
                }
                for (RequestMethod method : methods) {
                    if (method != RequestMethod.GET && method != RequestMethod.HEAD
                            && method != RequestMethod.OPTIONS) {
                        mappings.add(new WriteMapping(method.name(), pattern, entry.getValue()));
                    }
                }
            }
        }
        return mappings;
    }

    private static RequiresPermission declared(HandlerMethod handler) {
        RequiresPermission onMethod =
                AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresPermission.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresPermission.class);
    }

    @Test
    void thereAreStaffDecisionsToCover() {
        assertTrue(writeMappings().size() >= 20,
                "En az 20 personel yazma ucu bekleniyordu, tarama boşa koşuyor: " + writeMappings());
    }

    @Test
    void everyStaffWriteEitherResolvesAnAuditSubjectOrSaysWhyItHasNone() {
        List<String> failures = new ArrayList<>();

        for (WriteMapping mapping : writeMappings()) {
            Optional<AuditSubjectSource> source = AuditSubjectSource.of(mapping.resourceType());
            if (source.isEmpty()) {
                failures.add(mapping + " → kaynak türü '" + mapping.resourceType() + "' AuditSubjectSource'ta yok. "
                        + "Kararın konusu (USER|COMPANY) nasıl çözülüyor ya da neden yok, orada tanımlanmalı.");
                continue;
            }
            AuditSubjectSource declared = source.get();
            if (!declared.resolvesASubject()) {
                continue;
            }
            String variable = mapping.firstVariable().orElse(null);
            if (!declared.getVariable().equals(variable)) {
                failures.add(mapping + " → " + declared + " konuyu '{" + declared.getVariable()
                        + "}' yol değişkeninden çözer ama bu ucun ilk yol değişkeni "
                        + (variable == null ? "yok" : "'{" + variable + "}'")
                        + "; denetim satırının resourceId'si o kaydın kimliği olmaz.");
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everySourceWithoutASubjectCarriesAWrittenReason() {
        List<String> silent = new ArrayList<>();
        for (AuditSubjectSource source : AuditSubjectSource.values()) {
            boolean hasReason = source.getReasonWithoutSubject() != null
                    && !source.getReasonWithoutSubject().isBlank();
            if (source.resolvesASubject() == hasReason) {
                silent.add(source.name());
            }
        }
        assertTrue(silent.isEmpty(),
                "Konu çözen kaynak gerekçe taşımaz, çözmeyen kaynak gerekçe taşımak zorunda: " + silent);
    }

    @Test
    void everyDeclaredSourceStillGuardsAStaffWrite() {
        Set<AuditSubjectSource> unused = EnumSet.allOf(AuditSubjectSource.class);
        for (WriteMapping mapping : writeMappings()) {
            AuditSubjectSource.of(mapping.resourceType()).ifPresent(unused::remove);
        }
        assertTrue(unused.isEmpty(), "Artık hiçbir personel yazma ucuna karşılık gelmeyen kaynak: " + unused);
    }
}
