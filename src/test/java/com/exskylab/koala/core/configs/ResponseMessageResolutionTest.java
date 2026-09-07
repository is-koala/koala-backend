package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseMessageResolutionTest {

    private static final Path DTOS = Path.of("src", "main", "java", "com", "exskylab", "koala", "core", "dtos");

    private static final Pattern RAW_KEY = Pattern.compile("\"\\{([a-z][a-zA-Z0-9._-]*)}\"");
    private static final Pattern MESSAGE_CONSTANT = Pattern.compile("\\b\\w*Messages\\.[A-Z][A-Z0-9_]*");

    private final MessageResolvingAdvice advice = new MessageResolvingAdvice(new MessageResolver(messageSource()));

    private StaticMessageSource messageSource() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("user.updated.successfully", Locale.of("tr"), "Bilgileriniz güncellendi.");
        source.addMessage("user.updated.successfully", Locale.ENGLISH, "Your details have been updated.");
        return source;
    }

    private static class NestedPayload {
        private String message = "{user.updated.successfully}";

        public String getMessage() {
            return message;
        }
    }

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("PATCH", "/users/me")));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private SuccessDataResult<NestedPayload> envelope() {
        return new SuccessDataResult<>(new NestedPayload(), "{user.updated.successfully}", HttpStatus.OK);
    }

    @Test
    void theEnvelopeMessageIsResolvedInTheLanguageTheRequestAskedFor() {
        LocaleContextHolder.setLocale(Locale.of("tr"));
        SuccessDataResult<NestedPayload> turkish = envelope();
        advice.beforeBodyWrite(turkish, null, null, null, null, null);
        assertEquals("Bilgileriniz güncellendi.", turkish.getMessage());

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        SuccessDataResult<NestedPayload> english = envelope();
        advice.beforeBodyWrite(english, null, null, null, null, null);
        assertEquals("Your details have been updated.", english.getMessage());
    }

    @Test
    void theAdviceNeverReachesIntoNestedDtoFields() {
        LocaleContextHolder.setLocale(Locale.of("tr"));
        SuccessDataResult<NestedPayload> result = envelope();

        advice.beforeBodyWrite(result, null, null, null, null, null);

        assertEquals("{user.updated.successfully}", result.getData().getMessage(),
                "Advice ic ice DTO alanlarina dokunmuyor; bu bir kusur degil kapsam. Bir DTO alanina "
                        + "{anahtar} yazan herkes onu istemciye ham gonderir.");
    }

    @Test
    void noResponseDtoLeansOnAnAdviceThatWillNeverResolveIt() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : responseDtoFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);

            Matcher rawKey = RAW_KEY.matcher(source);
            while (rawKey.find()) {
                offenders.add(file.getFileName() + " -> " + rawKey.group());
            }

            Matcher constant = MESSAGE_CONSTANT.matcher(source);
            while (constant.find()) {
                offenders.add(file.getFileName() + " -> " + constant.group());
            }
        }

        assertTrue(offenders.isEmpty(),
                "Yanit DTO'su ham i18n anahtari tasiyor; MessageResolvingAdvice yalniz zarfin en ustundeki "
                        + "message alanini cozer, ic ice alanlara dokunmaz: " + offenders);
    }

    @Test
    void theScanActuallyReachesTheResponseDtos() throws IOException {
        assertTrue(responseDtoFiles().size() > 90,
                "Beklenenden az yanit DTO'su tarandi: " + responseDtoFiles().size());
    }

    private List<Path> responseDtoFiles() throws IOException {
        try (Stream<Path> files = Files.walk(DTOS)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> {
                        String path = file.toString();
                        return path.contains("/response/") || path.contains("/common/");
                    })
                    .toList();
        }
    }
}
