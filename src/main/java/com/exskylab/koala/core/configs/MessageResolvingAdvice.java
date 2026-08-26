package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.utilities.results.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class MessageResolvingAdvice implements ResponseBodyAdvice<Object> {

    private final MessageResolver messageResolver;

    public MessageResolvingAdvice(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Result result) {
            result.setMessage(messageResolver.resolve(result.getMessage()));
        }
        return body;
    }
}
