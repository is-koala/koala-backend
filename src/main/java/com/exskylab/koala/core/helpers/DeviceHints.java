package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

public record DeviceHints(String model, String os, String appVersion) {

    public static final String MODEL_HEADER = "X-Koala-Device-Model";
    public static final String OS_HEADER = "X-Koala-OS";
    public static final String APP_VERSION_HEADER = "X-Koala-App-Version";

    public static final DeviceHints NONE = new DeviceHints(null, null, null);

    private static final int MODEL_MAX = 64;
    private static final int OS_MAX = 64;
    private static final int APP_VERSION_MAX = 32;

    public static DeviceHints from(HttpServletRequest request) {
        if (request == null) {
            return NONE;
        }
        return of(request.getHeader(MODEL_HEADER), request.getHeader(OS_HEADER),
                request.getHeader(APP_VERSION_HEADER));
    }

    public static DeviceHints of(String model, String os, String appVersion) {
        String cleanModel = clean(model, MODEL_MAX);
        String cleanOs = clean(os, OS_MAX);
        String cleanVersion = clean(appVersion, APP_VERSION_MAX);
        if (cleanModel == null && cleanOs == null && cleanVersion == null) {
            return NONE;
        }
        return new DeviceHints(cleanModel, cleanOs, cleanVersion);
    }

    public boolean describesDevice() {
        return model != null || os != null;
    }

    private static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String printable = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (printable.isEmpty()) {
            return null;
        }
        return printable.length() > max ? printable.substring(0, max) : printable;
    }
}
