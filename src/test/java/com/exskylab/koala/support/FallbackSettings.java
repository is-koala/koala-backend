package com.exskylab.koala.support;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.entities.AppSetting;

import java.math.BigDecimal;
import java.util.List;

public class FallbackSettings implements SettingsService {

    public static CommissionCalculator commissionCalculator() {
        return new CommissionCalculator(new CommissionProperties(), new FallbackSettings());
    }

    public static CommissionCalculator commissionCalculator(CommissionProperties properties) {
        return new CommissionCalculator(properties, new FallbackSettings());
    }

    public static CommissionCalculator commissionCalculator(String rate) {
        CommissionProperties properties = new CommissionProperties();
        properties.setRate(new BigDecimal(rate));
        return commissionCalculator(properties);
    }

    @Override
    public int intValue(String key, int fallback) {
        return fallback;
    }

    @Override
    public String stringValue(String key, String fallback) {
        return fallback;
    }

    @Override
    public BigDecimal decimalValue(String key, BigDecimal fallback) {
        return fallback;
    }

    @Override
    public List<AppSetting> getAll() {
        return List.of();
    }

    @Override
    public AppSetting update(String key, String value) {
        throw new UnsupportedOperationException();
    }
}
