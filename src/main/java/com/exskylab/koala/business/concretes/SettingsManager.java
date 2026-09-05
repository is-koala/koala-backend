package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.core.helpers.AppVersion;
import com.exskylab.koala.core.constants.SettingMessages;
import com.exskylab.koala.dataAccess.AppSettingDao;
import com.exskylab.koala.entities.AppSetting;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SettingsManager implements SettingsService {

    private static final Set<String> KNOWN_KEYS = knownKeys();

    private static final Set<String> RATE_KEYS = Set.of(
            SettingKeys.COMMISSION_RATE, SettingKeys.CANCELLATION_LATE_FEE_RATE);

    private final AppSettingDao appSettingDao;

    public SettingsManager(AppSettingDao appSettingDao) {
        this.appSettingDao = appSettingDao;
    }

    private static Set<String> knownKeys() {
        return Arrays.stream(SettingKeys.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .map(SettingsManager::readConstant)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String readConstant(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String stringValue(String key, String fallback) {
        return appSettingDao.findByKey(key)
                .map(AppSetting::getValue)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    @Override
    @Transactional(readOnly = true)
    public int intValue(String key, int fallback) {
        return appSettingDao.findByKey(key)
                .map(AppSetting::getValue)
                .map(value -> parseInt(key, value, fallback))
                .orElse(fallback);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal decimalValue(String key, BigDecimal fallback) {
        return appSettingDao.findByKey(key)
                .map(AppSetting::getValue)
                .map(value -> parseDecimal(key, value, fallback))
                .orElse(fallback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppSetting> getAll() {
        return appSettingDao.findAllByOrderByKeyAsc();
    }

    @Override
    @Transactional
    public AppSetting update(String key, String value) {
        if (!KNOWN_KEYS.contains(key)) {
            throw new ValidationException(SettingMessages.UNKNOWN_KEY);
        }
        if (value == null || value.isBlank()) {
            throw new ValidationException(SettingMessages.VALUE_REQUIRED);
        }
        if (SettingKeys.MINIMUM_APP_VERSION.equals(key)) {
            if (!AppVersion.isParsable(value.trim())) {
                throw new ValidationException(SettingMessages.VALUE_NOT_A_VERSION);
            }
        } else if (!value.trim().matches("-?\\d+(\\.\\d+)?")) {
            throw new ValidationException(SettingMessages.VALUE_NOT_NUMERIC);
        } else if (RATE_KEYS.contains(key)) {
            BigDecimal rate = new BigDecimal(value.trim());
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new ValidationException(SettingMessages.VALUE_OUT_OF_RANGE);
            }
        }

        AppSetting setting = appSettingDao.findByKey(key).orElseGet(() -> {
            AppSetting created = new AppSetting();
            created.setKey(key);
            return created;
        });

        setting.setValue(value.trim());
        AppSetting saved = appSettingDao.save(setting);

        log.info("Setting {} changed to {}", key, saved.getValue());
        return saved;
    }

    private int parseInt(String key, String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Setting {} holds a non-numeric value '{}', falling back to {}", key, value, fallback);
            return fallback;
        }
    }

    private BigDecimal parseDecimal(String key, String value, BigDecimal fallback) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Setting {} holds a non-numeric value '{}', falling back to {}", key, value, fallback);
            return fallback;
        }
    }
}
