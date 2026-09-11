package com.exskylab.koala.core.constants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityMessageSurfaceTest {

    @Test
    void everyIdentityMessageKeyHasTurkishAndEnglishCopy() throws Exception {
        Properties tr = load("messages_tr.properties");
        Properties en = load("messages_en.properties");

        for (Field field : IdentityMessages.class.getDeclaredFields()) {
            String wrapped = (String) field.get(null);
            String key = wrapped.substring(1, wrapped.length() - 1);
            assertTrue(tr.containsKey(key), "messages_tr eksik: " + key);
            assertTrue(en.containsKey(key), "messages_en eksik: " + key);
            assertTrue(tr.getProperty(key).length() > 20, "TR metin kisa: " + key);
            assertTrue(en.getProperty(key).length() > 20, "EN metin kisa: " + key);
        }
    }

    @Test
    void untrustedSignerIsNotCollapsedIntoAGenericReadFailure() throws IOException {
        Properties tr = load("messages_tr.properties");
        String message = tr.getProperty("identity.untrusted.document.signer");

        assertTrue(message.contains("imzalayan"),
                "kullanici kartin okunamadigini degil makamin taninmadigini duymali");
        assertFalse(message.equals("Kartınız okunamadı.")
                || message.equals("kartınız okunamadı"),
                "ayrisik hata yuzeyi kaybolmus");
        assertTrue(tellsTheUserWhatToDo(message),
                "ne yapacagini soylemeli: " + message);
    }

    @Test
    void turkishIdentityMessagesTellTheUserWhatToDo() throws IOException {
        Properties tr = load("messages_tr.properties");

        for (String key : tr.stringPropertyNames()) {
            if (!key.startsWith("identity.") || key.endsWith(".created")) {
                continue;
            }
            String value = tr.getProperty(key);
            assertTrue(tellsTheUserWhatToDo(value),
                    key + " kullaniciya sonraki adimi soylemiyor: " + value);
        }
    }

    private static boolean tellsTheUserWhatToDo(String message) {
        String lower = message.toLowerCase();
        return lower.contains("tekrar")
                || lower.contains("okutun")
                || lower.contains("destek")
                || lower.contains("sonra")
                || lower.contains("uygulama")
                || lower.contains("eşleşmiyor");
    }

    private Properties load(String name) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, name + " bulunamadi");
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }
}
