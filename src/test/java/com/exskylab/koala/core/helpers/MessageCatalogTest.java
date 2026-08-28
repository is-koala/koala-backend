package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogTest {

    private static final Pattern KEY = Pattern.compile("\"\\{([a-z][a-zA-Z0-9.]*)}\"");

    @Test
    void everyKeyUsedInCodeHasATurkishAndEnglishMessage() throws IOException {
        Set<String> used = keysUsedInSource();
        Properties tr = load("messages_tr.properties");
        Properties en = load("messages_en.properties");

        List<String> missingTr = used.stream().filter(k -> !tr.containsKey(k)).sorted().toList();
        List<String> missingEn = used.stream().filter(k -> !en.containsKey(k)).sorted().toList();

        assertTrue(used.size() > 150, "Beklenenden az anahtar tarandi: " + used.size());
        assertTrue(missingTr.isEmpty(), "messages_tr.properties eksik: " + missingTr);
        assertTrue(missingEn.isEmpty(), "messages_en.properties eksik: " + missingEn);
    }

    @Test
    void theTwoCatalogsCoverExactlyTheSameKeys() throws IOException {
        Properties tr = load("messages_tr.properties");
        Properties en = load("messages_en.properties");

        List<String> onlyTr = tr.stringPropertyNames().stream().filter(k -> !en.containsKey(k)).sorted().toList();
        List<String> onlyEn = en.stringPropertyNames().stream().filter(k -> !tr.containsKey(k)).sorted().toList();

        assertTrue(onlyTr.isEmpty(), "Yalnizca TR'de olan anahtarlar: " + onlyTr);
        assertTrue(onlyEn.isEmpty(), "Yalnizca EN'de olan anahtarlar: " + onlyEn);
        assertEquals(tr.size(), en.size());
    }

    @Test
    void noMessageIsBlankAndNoneLeaksItsOwnKey() throws IOException {
        for (String file : new String[]{"messages_tr.properties", "messages_en.properties"}) {
            Properties props = load(file);
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                assertTrue(value != null && !value.isBlank(), file + " icinde bos deger: " + key);
                assertTrue(!value.equals(key), file + " icinde anahtar deger olarak yazilmis: " + key);
            }
        }
    }

    @Test
    void turkishMessagesSurviveAsUtf8() throws IOException {
        Properties tr = load("messages_tr.properties");

        assertTrue(tr.getProperty("user.not.found").contains("bulunamadı"),
                "Turkce karakterler bozulmus: " + tr.getProperty("user.not.found"));
        assertTrue(tr.stringPropertyNames().stream().noneMatch(k -> tr.getProperty(k).contains("?")
                        && !tr.getProperty(k).contains("? ")),
                "Bazi degerlerde soru isaretine donusmus karakter var.");
    }

    private Properties load(String name) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, name + " bulunamadi");
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    private Set<String> keysUsedInSource() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        Path root = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                Matcher matcher = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }
}
