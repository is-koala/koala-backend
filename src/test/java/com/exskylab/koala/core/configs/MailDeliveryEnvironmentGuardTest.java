package com.exskylab.koala.core.configs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.properties.DeploymentProperties;
import com.exskylab.koala.core.properties.EmailDeliveryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailDeliveryEnvironmentGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GuardConfiguration.class);

    private ListAppender<ILoggingEvent> appender;

    private Logger guardLogger;

    @BeforeEach
    void captureGuardOutput() {
        appender = new ListAppender<>();
        appender.start();
        guardLogger = (Logger) LoggerFactory.getLogger(MailDeliveryEnvironmentGuard.class);
        guardLogger.addAppender(appender);
    }

    @AfterEach
    void releaseGuardOutput() {
        guardLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void localUnrestrictedMailStillStartsForExistingIntegrationTests() {
        contextRunner.withPropertyValues("email.delivery.mode=UNRESTRICTED")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void stagingUnrestrictedMailWarnsAndStarts() {
        contextRunner.withPropertyValues(
                        "app.deployment=staging",
                        "email.delivery.mode=UNRESTRICTED")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("MAIL_DELIVERY_MODE"), warnings().toString());
                    assertTrue(warnings().stream().anyMatch(message ->
                            message.contains(MailDeliveryEnvironmentGuard.MARKER)));
                });
    }

    @Test
    void stagingAllowlistWithRecipientsStartsQuietly() {
        contextRunner.withPropertyValues(
                        "app.deployment=staging",
                        "email.delivery.mode=ALLOWLIST",
                        "email.delivery.allowed-recipients=qa-employer@example.test")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnings().isEmpty(), warnings().toString());
                });
    }

    @Test
    void emptyAllowlistWarnsInStagingWithoutPrintingRecipients() {
        contextRunner.withPropertyValues(
                        "app.deployment=staging",
                        "email.delivery.mode=ALLOWLIST")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("MAIL_ALLOWED_RECIPIENTS"), warnings().toString());
                    assertFalse(messages().stream().anyMatch(message -> message.contains("@")),
                            messages().toString());
                });
    }

    @Test
    void productionUnrestrictedWarnsAndStarts() {
        contextRunner.withPropertyValues(
                        "app.deployment=production",
                        "email.delivery.mode=UNRESTRICTED")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("MAIL_DELIVERY_MODE"), warnings().toString());
                    assertTrue(warnings().stream().anyMatch(message ->
                            message.contains(MailDeliveryEnvironmentGuard.MARKER)));
                });
    }

    @Test
    void emptyAllowlistWarnsInProduction() {
        contextRunner.withPropertyValues(
                        "app.deployment=production",
                        "email.delivery.mode=ALLOWLIST")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("MAIL_ALLOWED_RECIPIENTS"), warnings().toString());
                });
    }

    private boolean warnedAbout(String variable) {
        return warnings().stream().anyMatch(message -> message.contains(variable));
    }

    private List<String> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DeploymentProperties.class, EmailDeliveryProperties.class})
    @Import(MailDeliveryEnvironmentGuard.class)
    static class GuardConfiguration {}
}
