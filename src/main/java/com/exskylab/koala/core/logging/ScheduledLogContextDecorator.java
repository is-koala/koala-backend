package com.exskylab.koala.core.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public class ScheduledLogContextDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return () -> {
            LogContext.clear();
            MDC.put(LogContext.REQUEST_ID, LogContext.newRequestId());
            try {
                runnable.run();
            } finally {
                LogContext.clear();
            }
        };
    }
}
