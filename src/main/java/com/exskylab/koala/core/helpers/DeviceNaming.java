package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.DeviceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class DeviceNaming {

    public static final String UNKNOWN_DEVICE = "Bilinmeyen cihaz";
    public static final String SEPARATOR = " · ";
    public static final String WEB_SUFFIX = "web";
    public static final String APP_PREFIX = "Koala ";
    public static final int MAX_LENGTH = 120;

    public record ParsedAgent(String deviceName, String osNameVersion, String agentNameVersionMajor,
                              String osNameVersionMajor) {
    }

    private DeviceNaming() {
    }

    public static String name(DeviceHints hints, ParsedAgent agent, DeviceType type) {
        List<String> parts = hints.describesDevice()
                ? fromHints(hints)
                : type == DeviceType.WEB_BROWSER ? fromBrowser(agent) : fromAgent(agent);
        String joined = String.join(SEPARATOR, parts);
        if (joined.isBlank()) {
            return UNKNOWN_DEVICE;
        }
        return joined.length() > MAX_LENGTH ? joined.substring(0, MAX_LENGTH).strip() : joined;
    }

    private static List<String> fromHints(DeviceHints hints) {
        return known(AppleDeviceNames.marketingName(hints.model()), hints.os(),
                hints.appVersion() == null ? null : APP_PREFIX + hints.appVersion());
    }

    private static List<String> fromBrowser(ParsedAgent agent) {
        List<String> parts = known(agent.agentNameVersionMajor(), agent.osNameVersionMajor());
        if (parts.isEmpty()) {
            return parts;
        }
        List<String> withSuffix = new ArrayList<>(parts);
        withSuffix.add(WEB_SUFFIX);
        return withSuffix;
    }

    private static List<String> fromAgent(ParsedAgent agent) {
        return known(agent.deviceName(), agent.osNameVersion());
    }

    private static List<String> known(String... candidates) {
        return Stream.of(candidates)
                .map(DeviceNaming::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("??", "").replaceAll("\\s+", " ").strip();
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.startsWith("unknown") || lower.equals("hacker") || lower.equals("robot")) {
            return "";
        }
        return cleaned;
    }
}
