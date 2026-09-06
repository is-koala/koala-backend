package com.exskylab.koala.core.logging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

@Component
public class LogContextPublishPostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        LogContext.carried().forEach((key, value) ->
                message.getMessageProperties().setHeader(LogContext.HEADER_PREFIX + key, value));

        String requestId = LogContext.requestId();
        if (requestId != null && message.getMessageProperties().getCorrelationId() == null) {
            message.getMessageProperties().setCorrelationId(requestId);
        }
        return message;
    }
}
