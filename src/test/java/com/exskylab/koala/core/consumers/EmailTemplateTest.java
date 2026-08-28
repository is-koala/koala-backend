package com.exskylab.koala.core.consumers;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    private static final Path SOURCES = Paths.get("src/main/java/com/exskylab/koala");
    private static final Path TEMPLATES = Paths.get("src/main/resources/templates/emails");

    private static final Pattern EMAIL_TEMPLATE_NAME =
            Pattern.compile("setTemplateName\\(\\s*(?:\\w+\\s*\\?\\s*)?\"([a-z0-9-]+)\"(?:\\s*:\\s*\"([a-z0-9-]+)\")?");

    private static final List<String> NON_EMAIL_TEMPLATES = List.of("raw-sms-template", "raw-push-template");

    private static TreeSet<String> referencedTemplates() throws IOException {
        TreeSet<String> names = new TreeSet<>();

        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher matcher = EMAIL_TEMPLATE_NAME.matcher(Files.readString(file));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                    if (matcher.group(2) != null) {
                        names.add(matcher.group(2));
                    }
                }
            }
        }
        names.removeAll(NON_EMAIL_TEMPLATES);
        return names;
    }

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    @Test
    void everyTemplateNameUsedInCodeHasAFile() throws IOException {
        List<String> missing = new ArrayList<>();

        for (String name : referencedTemplates()) {
            if (!Files.exists(TEMPLATES.resolve(name + ".html"))) {
                missing.add(name);
            }
        }

        assertTrue(missing.isEmpty(),
                "kodda kullanilan ama dosyasi olmayan sablon: " + missing);
    }

    @Test
    void everyTemplateRendersWithoutBlowingUp() throws IOException {
        SpringTemplateEngine templateEngine = engine();

        Context context = new Context();
        context.setVariables(Map.ofEntries(
                Map.entry("firstName", "Yusuf"),
                Map.entry("lastName", "Acmaci"),
                Map.entry("email", "yeni@koala.test"),
                Map.entry("verificationCode", "123456"),
                Map.entry("verificationLink", "https://api.iskoala.com/verify"),
                Map.entry("companyName", "Koala AS"),
                Map.entry("companyId", "9a1c0f10-0001-4c00-8000-000000000001"),
                Map.entry("companyEmail", "sirket@koala.test"),
                Map.entry("contactFirstName", "Ayse"),
                Map.entry("contactLastName", "Yilmaz"),
                Map.entry("contactId", "9a1c0f10-0002-4c00-8000-000000000002"),
                Map.entry("contactRole", "ADMIN"),
                Map.entry("contactEmail", "yetkili@koala.test"),
                Map.entry("invitedByFirstName", "Mehmet"),
                Map.entry("invitedByLastName", "Demir"),
                Map.entry("invitedUserFirstName", "Ayse"),
                Map.entry("invitedUserLastName", "Yilmaz"),
                Map.entry("role", "ADMIN"),
                Map.entry("trackingUrl", "https://api.iskoala.com/notification-tracking/x.png")));

        List<String> broken = new ArrayList<>();

        for (String name : referencedTemplates()) {
            try {
                String html = templateEngine.process("emails/" + name, context);
                if (html == null || html.isBlank()) {
                    broken.add(name + " (bos cikti)");
                }
            } catch (Exception e) {
                broken.add(name + " -> " + e.getMessage());
            }
        }

        assertTrue(broken.isEmpty(), "islenemeyen sablon: " + String.join("\n", broken));
    }

    @Test
    void noTemplateStillPointsAtTheRetiredApiPrefix() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("apiUrl() + \"/api/")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }

        assertFalse(offenders.stream().findAny().isPresent(),
                "uc yollari /api onekini tasimiyor, bu adres 404 doner: " + offenders);
    }
}
