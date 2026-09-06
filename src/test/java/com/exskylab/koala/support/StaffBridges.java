package com.exskylab.koala.support;

import com.exskylab.koala.core.properties.StaffBridgeProperties;

public final class StaffBridges {

    private StaffBridges() {
    }

    public static StaffBridgeProperties open() {
        return withOpen(true);
    }

    public static StaffBridgeProperties closed() {
        return withOpen(false);
    }

    private static StaffBridgeProperties withOpen(boolean open) {
        StaffBridgeProperties properties = new StaffBridgeProperties();
        properties.setOpen(open);
        return properties;
    }
}
