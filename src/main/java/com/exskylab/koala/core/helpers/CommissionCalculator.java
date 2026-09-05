package com.exskylab.koala.core.helpers;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.CommissionProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CommissionCalculator {

    private static final int MONEY_SCALE = 2;

    private final CommissionProperties commissionProperties;
    private final SettingsService settingsService;

    public CommissionCalculator(CommissionProperties commissionProperties, SettingsService settingsService) {
        this.commissionProperties = commissionProperties;
        this.settingsService = settingsService;
    }

    public Breakdown calculate(BigDecimal workerPayout) {
        if (workerPayout == null || workerPayout.signum() <= 0) {
            throw new ValidationException("{job.salary.min.value}");
        }

        BigDecimal rate = rate();
        BigDecimal payout = workerPayout.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal commission = payout.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new Breakdown(payout, commission, payout.add(commission), rate);
    }

    private BigDecimal rate() {
        return settingsService.decimalValue(SettingKeys.COMMISSION_RATE, commissionProperties.getRate());
    }

    public record Breakdown(BigDecimal workerPayout, BigDecimal commission,
                            BigDecimal totalCharged, BigDecimal rate) {
    }
}
