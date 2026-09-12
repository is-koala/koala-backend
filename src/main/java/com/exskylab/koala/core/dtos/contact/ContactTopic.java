package com.exskylab.koala.core.dtos.contact;

import java.util.Locale;

public enum ContactTopic {

    DESTEK("destek", "Destek"),
    KVKK("kvkk", "KVKK"),
    SIKAYET("sikayet", "Şikayet"),
    DIGER("diger", "Diğer");

    private final String wireValue;
    private final String label;

    ContactTopic(String wireValue, String label) {
        this.wireValue = wireValue;
        this.label = label;
    }

    public String wireValue() {
        return wireValue;
    }

    public String label() {
        return label;
    }

    public static ContactTopic fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (ContactTopic topic : values()) {
            if (topic.wireValue.equals(normalized)) {
                return topic;
            }
        }
        return null;
    }
}
