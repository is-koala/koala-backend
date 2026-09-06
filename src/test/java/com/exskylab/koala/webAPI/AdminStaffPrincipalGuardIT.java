package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #95: {@code SecurityManager.getAuthenticatedPrincipal()} marketplace bir {@code UserPrincipal} bekler.
 * {@code StaffPrincipal} bir {@code UserPrincipal} değildir; bu yüzden {@code /admin/**} altındaki bir
 * metodun {@code securityService.getAuthenticated*} çağırması — bir log satırında bile — personel
 * kimliğiyle gelen isteği öldürür. Dört örnek düzeltildi, bu test beşincisinin sessizce eklenmesini
 * engeller: CLAUDE.md §0b'deki yazılı kural tek başına koruma değildir (#88).
 *
 * <p><b>Kapsam bir seviye derindir: handler → handler'ın çağırdığı manager metodu.</b> Manager başka
 * bir manager'ı ya da kendi özel yardımcısını çağırır ve tuzak orada olursa bu tarama görmez. Görülen
 * şey #95'in dört örneğinin de sahip olduğu biçimdir; daha derin çağrı zincirleri hâlâ
 * {@code CompanyAdministrationRulesTest} ve {@code RoleGrantRulesTest} gibi davranış testlerine emanet.</p>
 *
 * <p>Köprü yolu bilinçli istisnadır: aktörünü {@code StaffPrincipal} parametresiyle alan bir metot
 * {@code actor == null} dalında securityService'e uzanabilir, çünkü orada kimlik zaten bir marketplace
 * kullanıcısıdır. Parametreyi almayan metotta böyle bir dal yoktur, çağrı koşulsuzdur.</p>
 */
class AdminStaffPrincipalGuardIT extends AbstractIntegrationTest {

    private static final String ADMIN_PREFIX = "/admin";
    private static final String AUTHENTICATION_PREFIX = "/admin/auth/";
    private static final String TRAP = "securityService.getAuthenticated";

    private static final Pattern SERVICE_CALL = Pattern.compile("(\\w+Service)\\.(\\w+)\\(");
    private static final Pattern STAFF_ACTOR = Pattern.compile("StaffPrincipal\\s+(\\w+)");

    private static final int SMALLEST_CREDIBLE_REACH = 30;

    private static final List<String> THE_FOUR_THAT_WERE_BROKEN = List.of(
            "CompanyManager.approveCompany",
            "CompanyManager.rejectCompany",
            "UserManager.grantRole",
            "UserManager.revokeRole");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record AdminMapping(String verb, String pattern, HandlerMethod handler) {

        @Override
        public String toString() {
            return verb + " " + pattern + " (" + handler.getBeanType().getSimpleName() + "."
                    + handler.getMethod().getName() + ")";
        }
    }

    private record MethodSource(int offset, String parameters, String body) {
    }

    private record Reach(AdminMapping mapping, String field, Class<?> serviceType, String method) {

        String signature() {
            return serviceType.getSimpleName() + "." + method;
        }

        @Override
        public String toString() {
            return mapping + " → " + field + "." + method + " → " + signature();
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
        return methods.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String sourceOf(Class<?> type) throws IOException {
        String name = type.getName();
        int nested = name.indexOf('$');
        if (nested >= 0) {
            name = name.substring(0, nested);
        }
        Path file = Paths.get("src/main/java/" + name.replace('.', '/') + ".java");
        return Files.exists(file) ? Files.readString(file) : "";
    }

    private static int matching(String source, int opening, char open, char close) {
        int depth = 0;
        for (int index = opening; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == open) {
                depth++;
            } else if (character == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static List<MethodSource> declarationsOf(String source, String methodName) {
        List<MethodSource> declarations = new ArrayList<>();
        Matcher candidate = Pattern.compile("(?<![\\w.])" + Pattern.quote(methodName) + "\\s*\\(").matcher(source);

        while (candidate.find()) {
            int preceding = candidate.start() - 1;
            while (preceding >= 0 && Character.isWhitespace(source.charAt(preceding))) {
                preceding--;
            }
            if (preceding < 0 || source.charAt(preceding) == '.') {
                continue;
            }

            int openParenthesis = candidate.end() - 1;
            int closeParenthesis = matching(source, openParenthesis, '(', ')');
            if (closeParenthesis < 0) {
                continue;
            }

            int cursor = closeParenthesis + 1;
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            if (source.startsWith("throws", cursor)) {
                cursor = source.indexOf('{', cursor);
            }
            if (cursor < 0 || cursor >= source.length() || source.charAt(cursor) != '{') {
                continue;
            }

            int closingBrace = matching(source, cursor, '{', '}');
            if (closingBrace < 0) {
                continue;
            }
            declarations.add(new MethodSource(cursor,
                    source.substring(openParenthesis + 1, closeParenthesis),
                    source.substring(cursor, closingBrace + 1)));
        }
        return declarations;
    }

    private static boolean onTheBridgePath(String body, int position, Pattern bridge) {
        Deque<String> headers = new ArrayDeque<>();
        int segment = 0;

        for (int index = 0; index < position; index++) {
            char character = body.charAt(index);
            if (character == '{') {
                headers.push(body.substring(segment, index));
                segment = index + 1;
            } else if (character == '}') {
                if (!headers.isEmpty()) {
                    headers.pop();
                }
                segment = index + 1;
            } else if (character == ';') {
                segment = index + 1;
            }
        }

        if (bridge.matcher(body.substring(segment, position)).find()) {
            return true;
        }
        return headers.stream().anyMatch(header -> bridge.matcher(header).find());
    }

    private static int lineOf(String source, int position) {
        return (int) source.substring(0, position).chars().filter(character -> character == '\n').count() + 1;
    }

    private static List<Integer> trapLines(String source, MethodSource method) {
        Matcher actor = STAFF_ACTOR.matcher(method.parameters());
        Pattern bridge = actor.find()
                ? Pattern.compile("\\b" + Pattern.quote(actor.group(1)) + "\\s*==\\s*null\\b")
                : null;

        List<Integer> lines = new ArrayList<>();
        int found = method.body().indexOf(TRAP);
        while (found >= 0) {
            if (bridge == null || !onTheBridgePath(method.body(), found, bridge)) {
                lines.add(lineOf(source, method.offset() + found));
            }
            found = method.body().indexOf(TRAP, found + TRAP.length());
        }
        return lines;
    }

    private Class<?> serviceBehind(HandlerMethod handler, String field) {
        Object controller = AopTestUtils.getUltimateTargetObject(handler.createWithResolvedBean().getBean());
        Field declared = ReflectionUtils.findField(handler.getBeanType(), field);
        if (declared == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(declared);
        Object service = ReflectionUtils.getField(declared, controller);
        return service == null ? null : AopProxyUtils.ultimateTargetClass(service);
    }

    private List<Reach> reachedManagerMethods(List<String> unresolved) throws IOException {
        List<Reach> reached = new ArrayList<>();

        for (AdminMapping mapping : adminMappings()) {
            String controllerSource = sourceOf(mapping.handler().getBeanType());

            for (MethodSource handlerBody : declarationsOf(controllerSource, mapping.handler().getMethod().getName())) {
                Matcher call = SERVICE_CALL.matcher(handlerBody.body());
                while (call.find()) {
                    Class<?> serviceType = serviceBehind(mapping.handler(), call.group(1));
                    if (serviceType == null) {
                        unresolved.add(mapping + " → " + call.group(1));
                        continue;
                    }
                    reached.add(new Reach(mapping, call.group(1), serviceType, call.group(2)));
                }
            }
        }
        return reached;
    }

    @Test
    void theScanReachesTheManagersBehindTheAdminSurface() throws IOException {
        List<String> unresolved = new ArrayList<>();
        Set<String> signatures = reachedManagerMethods(unresolved).stream()
                .map(Reach::signature)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(unresolved.isEmpty(),
                "Bean'i çözülemeyen servis alanı — tarama burada sessizce korumayı bıraktı: " + unresolved);

        assertTrue(signatures.size() >= SMALLEST_CREDIBLE_REACH,
                "Tarama yalnızca " + signatures.size() + " manager metodu gördü; en az " + SMALLEST_CREDIBLE_REACH
                        + " bekleniyordu. Bu sayı düşerse bozulan taramanın kendisidir ve aşağıdaki değişmez "
                        + "boşa koşar: " + signatures);

        assertTrue(signatures.containsAll(THE_FOUR_THAT_WERE_BROKEN),
                "#95'in düzelttiği dört metot taramanın içinde olmalı; biri kaybolduysa handler → manager "
                        + "çözümlemesi artık çalışmıyor demektir. Görülen: " + signatures);
    }

    @Test
    void noAdminEndpointReachesTheMarketplacePrincipal() throws IOException {
        List<String> traps = new ArrayList<>();

        for (AdminMapping mapping : adminMappings()) {
            String controllerSource = sourceOf(mapping.handler().getBeanType());
            for (MethodSource handlerBody : declarationsOf(controllerSource, mapping.handler().getMethod().getName())) {
                for (int line : trapLines(controllerSource, handlerBody)) {
                    traps.add(mapping + " → " + mapping.handler().getBeanType().getSimpleName() + ".java:" + line);
                }
            }
        }

        for (Reach reach : reachedManagerMethods(new ArrayList<>())) {
            String managerSource = sourceOf(reach.serviceType());
            for (MethodSource managerBody : declarationsOf(managerSource, reach.method())) {
                for (int line : trapLines(managerSource, managerBody)) {
                    traps.add(reach + " → " + reach.serviceType().getSimpleName() + ".java:" + line);
                }
            }
        }

        assertTrue(traps.isEmpty(),
                "/admin/** altındaki bir metot " + TRAP + "* çağırıyor — bir log satırında bile olsa personel "
                        + "kimliğiyle gelen istek IllegalStateException ile ölüyor (#95). Aktörü "
                        + "@AuthenticationPrincipal StaffPrincipal actor ile parametre olarak al; "
                        + "securityService yalnızca actor == null köprü yolunda çağrılabilir. "
                        + new TreeSet<>(traps));
    }
}
