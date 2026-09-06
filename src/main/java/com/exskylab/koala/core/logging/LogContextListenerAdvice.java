package com.exskylab.koala.core.logging;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LogContextListenerAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        Object payload = arguments.length > 1 ? arguments[1] : null;

        LogContext.adopt(contextOf(messageOf(payload)));
        try {
            return invocation.proceed();
        } finally {
            LogContext.clear();
        }
    }

    private static Message messageOf(Object payload) {
        if (payload instanceof Message message) {
            return message;
        }
        if (payload instanceof List<?> batch && !batch.isEmpty() && batch.getFirst() instanceof Message message) {
            return message;
        }
        return null;
    }

    private static Map<String, String> contextOf(Message message) {
        Map<String, String> values = new LinkedHashMap<>();
        if (message != null) {
            for (String key : LogContext.CARRIED_KEYS) {
                Object header = message.getMessageProperties().getHeader(LogContext.HEADER_PREFIX + key);
                if (header != null) {
                    values.put(key, header.toString());
                }
            }
            String correlationId = message.getMessageProperties().getCorrelationId();
            if (correlationId != null) {
                values.putIfAbsent(LogContext.REQUEST_ID, correlationId);
            }
        }
        values.putIfAbsent(LogContext.REQUEST_ID, LogContext.newRequestId());
        return values;
    }
}
