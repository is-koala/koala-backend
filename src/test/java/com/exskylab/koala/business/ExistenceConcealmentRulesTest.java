package com.exskylab.koala.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistenceConcealmentRulesTest {

    private static final Path MANAGERS = Path.of("src", "main", "java", "com", "exskylab", "koala",
            "business", "concretes");

    private static final Pattern DIRECT = Pattern.compile("new\\s+ResourceNotFoundException\\(\\s*([^,)]+?)\\s*[,)]");
    private static final Pattern CONCEALED = Pattern.compile("Concealment\\.notFound\\(\\s*([^,)]+?)\\s*[,)]");
    private static final Pattern MEMBER_NAME = Pattern.compile("(\\w+)\\s*\\([^()]*\\)\\s*(?:throws [\\w, .]+)?\\s*$");

    private static final Set<String> TWO_RESOURCES_IN_ONE_METHOD = Set.of("PayoutManager.java#releaseToWorker");

    private record Site(Path file, String member, Set<String> keys) {

        String id() {
            return file.getFileName() + "#" + member;
        }
    }

    @Test
    void noSingleMethodAnswersWithTwoDifferentNotFoundKeys() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Site site : sites()) {
            if (site.keys().size() > 1 && !TWO_RESOURCES_IN_ONE_METHOD.contains(site.id())) {
                offenders.add(site.id() + " " + site.keys());
            }
        }

        assertTrue(offenders.isEmpty(),
                "Aynı metot iki farklı 404 anahtarı döndürüyor, errorCode kaynağın varlığını sızdırır (§2d, §2o): "
                        + offenders);
    }

    @Test
    void everyExemptionStillDescribesARealMethodThatNamesTwoResources() throws IOException {
        Set<String> stillNamingTwoResources = new LinkedHashSet<>();
        for (Site site : sites()) {
            if (site.keys().size() > 1) {
                stillNamingTwoResources.add(site.id());
            }
        }

        List<String> stale = TWO_RESOURCES_IN_ONE_METHOD.stream()
                .filter(exemption -> !stillNamingTwoResources.contains(exemption))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(), "Muafiyet listesi bayatlamış, gerekmeyen satırlar düşürülmeli: " + stale);
    }

    @Test
    void everyConcealedNotFoundIsNamedAfterTheMissingResource() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : managerFiles()) {
            Matcher concealed = CONCEALED.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (concealed.find()) {
                String key = concealed.group(1);
                if (!key.endsWith("NOT_FOUND")) {
                    offenders.add(file.getFileName() + " -> " + key);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Gizlenen 404 başarısız kontrolün değil eksik kaynağın adını taşımalı: " + offenders);
    }

    @Test
    void theConcealmentHelperIsTheOnlyPlaceThatDefinesTheConcealedResponse() throws IOException {
        Path helper = Path.of("src", "main", "java", "com", "exskylab", "koala", "core", "helpers",
                "Concealment.java");
        String source = Files.readString(helper, StandardCharsets.UTF_8);

        assertTrue(source.contains("log.warn"),
                "Gizleme ayrımı yalnız sunucu logunda yaşıyor, log satırı kaldırılamaz");
        assertEquals(1, source.split("new ResourceNotFoundException", -1).length - 1,
                "Gizlenen yanıt tek yerde üretilmeli");

        long callers = 0;
        for (Path file : managerFiles()) {
            if (Files.readString(file, StandardCharsets.UTF_8).contains("Concealment.notFound(")) {
                callers++;
            }
        }
        assertTrue(callers >= 7, "Gizleme yardımcısını kullanan manager sayısı beklenenden az: " + callers);
    }

    private static List<Site> sites() throws IOException {
        List<Site> sites = new ArrayList<>();

        for (Path file : managerFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            int depth = 0;
            int memberStart = -1;

            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '{') {
                    depth++;
                    if (depth == 2) {
                        memberStart = i;
                    }
                } else if (c == '}') {
                    depth--;
                    if (depth == 1 && memberStart >= 0) {
                        String body = source.substring(memberStart, i);
                        Set<String> keys = keysIn(body);
                        if (!keys.isEmpty()) {
                            sites.add(new Site(file, memberNameBefore(source, memberStart), keys));
                        }
                        memberStart = -1;
                    }
                }
            }
        }
        return sites;
    }

    private static Set<String> keysIn(String body) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher direct = DIRECT.matcher(body);
        while (direct.find()) {
            keys.add(direct.group(1));
        }
        Matcher concealed = CONCEALED.matcher(body);
        while (concealed.find()) {
            keys.add(concealed.group(1));
        }
        return keys;
    }

    private static String memberNameBefore(String source, int braceIndex) {
        String window = source.substring(Math.max(0, braceIndex - 400), braceIndex);
        Matcher name = MEMBER_NAME.matcher(window);
        String found = "<bilinmeyen>";
        while (name.find()) {
            found = name.group(1);
        }
        return found;
    }

    private static List<Path> managerFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MANAGERS)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
