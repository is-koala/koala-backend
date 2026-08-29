package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.AppSetting;

import java.math.BigDecimal;
import java.util.List;

public interface SettingsService {

    int intValue(String key, int fallback);

    String stringValue(String key, String fallback);

    BigDecimal decimalValue(String key, BigDecimal fallback);

    List<AppSetting> getAll();

    AppSetting update(String key, String value);
}
