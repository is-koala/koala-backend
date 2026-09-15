package com.exskylab.koala.core.helpers;

import java.util.Locale;
import java.util.Map;

public final class AppleDeviceNames {

    private static final Map<String, String> MARKETING_NAMES = Map.ofEntries(
            Map.entry("iphone12,1", "iPhone 11"),
            Map.entry("iphone12,3", "iPhone 11 Pro"),
            Map.entry("iphone12,5", "iPhone 11 Pro Max"),
            Map.entry("iphone12,8", "iPhone SE (2. nesil)"),
            Map.entry("iphone13,1", "iPhone 12 mini"),
            Map.entry("iphone13,2", "iPhone 12"),
            Map.entry("iphone13,3", "iPhone 12 Pro"),
            Map.entry("iphone13,4", "iPhone 12 Pro Max"),
            Map.entry("iphone14,2", "iPhone 13 Pro"),
            Map.entry("iphone14,3", "iPhone 13 Pro Max"),
            Map.entry("iphone14,4", "iPhone 13 mini"),
            Map.entry("iphone14,5", "iPhone 13"),
            Map.entry("iphone14,6", "iPhone SE (3. nesil)"),
            Map.entry("iphone14,7", "iPhone 14"),
            Map.entry("iphone14,8", "iPhone 14 Plus"),
            Map.entry("iphone15,2", "iPhone 14 Pro"),
            Map.entry("iphone15,3", "iPhone 14 Pro Max"),
            Map.entry("iphone15,4", "iPhone 15"),
            Map.entry("iphone15,5", "iPhone 15 Plus"),
            Map.entry("iphone16,1", "iPhone 15 Pro"),
            Map.entry("iphone16,2", "iPhone 15 Pro Max"),
            Map.entry("iphone17,1", "iPhone 16 Pro"),
            Map.entry("iphone17,2", "iPhone 16 Pro Max"),
            Map.entry("iphone17,3", "iPhone 16"),
            Map.entry("iphone17,4", "iPhone 16 Plus"),
            Map.entry("iphone17,5", "iPhone 16e"),
            Map.entry("ipad13,1", "iPad Air (4. nesil)"),
            Map.entry("ipad13,2", "iPad Air (4. nesil)"),
            Map.entry("ipad13,16", "iPad Air (5. nesil)"),
            Map.entry("ipad13,17", "iPad Air (5. nesil)"),
            Map.entry("ipad13,18", "iPad (10. nesil)"),
            Map.entry("ipad13,19", "iPad (10. nesil)"),
            Map.entry("ipad14,1", "iPad mini (6. nesil)"),
            Map.entry("ipad14,2", "iPad mini (6. nesil)"),
            Map.entry("ipad14,3", "iPad Pro 11 inç (4. nesil)"),
            Map.entry("ipad14,4", "iPad Pro 11 inç (4. nesil)"),
            Map.entry("ipad14,5", "iPad Pro 12,9 inç (6. nesil)"),
            Map.entry("ipad14,6", "iPad Pro 12,9 inç (6. nesil)"));

    private AppleDeviceNames() {
    }

    public static String marketingName(String model) {
        if (model == null) {
            return null;
        }
        String key = model.strip().toLowerCase(Locale.ROOT);
        return MARKETING_NAMES.getOrDefault(key, model.strip());
    }
}
