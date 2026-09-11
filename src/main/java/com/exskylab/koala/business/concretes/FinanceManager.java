package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.FinanceService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.constants.FinanceMessages;
import com.exskylab.koala.core.dtos.finance.response.FinanceSummaryDto;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class FinanceManager implements FinanceService {

    private static final String DEFAULT_CURRENCY = "TRY";
    private static final Instant BEGINNING = Instant.EPOCH;

    private final PaymentService paymentService;
    private final PayoutService payoutService;
    private final Clock clock;

    public FinanceManager(PaymentService paymentService, PayoutService payoutService, Clock clock) {
        this.paymentService = paymentService;
        this.payoutService = payoutService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryDto summarize(Instant fromInclusive, Instant toExclusive, String currency) {
        Instant from = fromInclusive;
        Instant to = toExclusive;
        if (from == null && to == null) {
            from = BEGINNING;
            to = clock.instant();
        } else if (from == null || to == null || !from.isBefore(to)) {
            throw new ValidationException(FinanceMessages.PERIOD_INVALID);
        }
        String normalized = normalizeCurrency(currency);
        PaymentService.SucceededVolume payments = paymentService.succeededVolume(from, to, normalized);
        PayoutService.AmountCount completed = payoutService.completedVolume(from, to, normalized);
        PayoutService.AmountCount awaiting = payoutService.awaitingVolume(normalized);
        return new FinanceSummaryDto(from, to, normalized,
                money(payments.employerVolume()), money(payments.koalaFee()), money(payments.workerPayout()),
                payments.count(),
                money(completed.amount()), completed.count(),
                money(awaiting.amount()), awaiting.count());
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new ValidationException(FinanceMessages.CURRENCY_INVALID);
        }
        return normalized;
    }

    private static String money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
