package com.exskylab.koala.core.monitoring;

public interface BackendErrorMonitor {

    void capture(Throwable error, String code);
}
