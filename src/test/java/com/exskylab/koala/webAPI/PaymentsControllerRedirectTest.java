package com.exskylab.koala.webAPI;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.helpers.PaymentReturnUrlBuilder;
import com.exskylab.koala.core.properties.PaymentReturnProperties;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentsControllerRedirectTest {

    private static final UUID ASSIGNMENT_ID = UUID.fromString("352e3e2f-68df-47b8-baf2-31d03b8200a7");
    private static final String TOKEN = "provider-token-7f3a9c";

    private PaymentService paymentService;
    private PaymentsController controller;
    private ListAppender<ILoggingEvent> appender;
    private Logger controllerLogger;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        PaymentReturnProperties properties = new PaymentReturnProperties();
        properties.setReturnBaseUrl("koala://");
        controller = new PaymentsController(paymentService, null, new PaymentReturnUrlBuilder(properties), null);

        appender = new ListAppender<>();
        appender.start();
        controllerLogger = (Logger) LoggerFactory.getLogger(PaymentsController.class);
        controllerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void callbackRedirectUsesThePaymentReturnBaseInsteadOfTheWebBase() {
        when(paymentService.completeFromCallback(TOKEN)).thenReturn(payment(PaymentAttemptStatus.SUCCEEDED));

        assertEquals(
                "koala://payment-result?status=success&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                controller.handleProviderCallback(TOKEN).getUrl());
    }

    @Test
    void theGetReplayRedirectsToTheRecordedOutcomeWithoutCompletingTheCallback() {
        when(paymentService.findRecordedOutcome(TOKEN)).thenReturn(Optional.of(
                new PaymentService.RecordedOutcome(PaymentAttemptStatus.SUCCEEDED, ASSIGNMENT_ID)));

        assertEquals(
                "koala://payment-result?status=success&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                controller.showProviderCallbackOutcome(TOKEN).getUrl());
        verify(paymentService, never()).completeFromCallback(any());
    }

    @Test
    void aRecordedFailureReplaysAsFailedWithTheAssignment() {
        when(paymentService.findRecordedOutcome(TOKEN)).thenReturn(Optional.of(
                new PaymentService.RecordedOutcome(PaymentAttemptStatus.FAILED, ASSIGNMENT_ID)));

        assertEquals(
                "koala://payment-result?status=failed&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                controller.showProviderCallbackOutcome(TOKEN).getUrl());
    }

    @Test
    void anAttemptStillPendingReplaysAsFailedExactlyLikeThePostWould() {
        when(paymentService.findRecordedOutcome(TOKEN)).thenReturn(Optional.of(
                new PaymentService.RecordedOutcome(PaymentAttemptStatus.VERIFY_PENDING, ASSIGNMENT_ID)));
        when(paymentService.completeFromCallback(TOKEN)).thenReturn(payment(PaymentAttemptStatus.VERIFY_PENDING));

        assertEquals(controller.handleProviderCallback(TOKEN).getUrl(),
                controller.showProviderCallbackOutcome(TOKEN).getUrl());
    }

    @Test
    void anUnknownTokenOverGetLandsOnTheResultPageAsFailedWithoutSayingWhy() {
        when(paymentService.findRecordedOutcome("sahte")).thenReturn(Optional.empty());

        assertEquals("koala://payment-result?status=failed",
                controller.showProviderCallbackOutcome("sahte").getUrl());
        verify(paymentService, never()).completeFromCallback(any());
    }

    @Test
    void theLogLineNamesTheChannelAndNeverTheToken() {
        when(paymentService.completeFromCallback(TOKEN)).thenReturn(payment(PaymentAttemptStatus.SUCCEEDED));
        when(paymentService.findRecordedOutcome(TOKEN)).thenReturn(Optional.of(
                new PaymentService.RecordedOutcome(PaymentAttemptStatus.SUCCEEDED, ASSIGNMENT_ID)));

        controller.handleProviderCallback(TOKEN);
        controller.showProviderCallbackOutcome(TOKEN);

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("POST"), lines.get(0));
        assertTrue(lines.get(1).contains("GET"), lines.get(1));
        assertFalse(lines.stream().anyMatch(line -> line.contains(TOKEN)),
                "provider token bir korelasyon sirridir, gunluge yazilmaz: " + lines);
    }

    private static Payment payment(PaymentAttemptStatus status) {
        Payment payment = new Payment();
        payment.setStatus(status);
        JobAssignment assignment = new JobAssignment();
        assignment.setId(ASSIGNMENT_ID);
        payment.setJobAssignment(assignment);
        return payment;
    }
}
