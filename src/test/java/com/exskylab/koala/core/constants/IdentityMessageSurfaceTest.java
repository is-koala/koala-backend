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

    private static final String[] DOCUMENT_UPLOAD_KEYS = {
            "identity.document.review.required",
            "identity.documents.required",
            "identity.documents.not.found"
    };

    private static final String FOUR_PHOTOS_TR =
            "yüz, TCKK ön, TCKK arka ve TCKK yüzün altında";
    private static final String FOUR_PHOTOS_EN =
            "selfie, TCKK front, TCKK back and TCKK under the face";

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

    @Test
    void documentUploadMessagesNameAllFourPhotosInBothCatalogues() throws IOException {
        Properties tr = load("messages_tr.properties");
        Properties en = load("messages_en.properties");

        for (String key : DOCUMENT_UPLOAD_KEYS) {
            String turkish = tr.getProperty(key);
            String english = en.getProperty(key);
            assertTrue(turkish.contains(FOUR_PHOTOS_TR),
                    key + " TR dort fotografi adlandirmiyor: " + turkish);
            assertTrue(english.contains(FOUR_PHOTOS_EN),
                    key + " EN dort fotografi adlandirmiyor: " + english);
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
