package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.dataAccess.AppSettingDao;
import com.exskylab.koala.entities.AppSetting;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettingsRulesTest {

    private AppSettingDao appSettingDao;
    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        appSettingDao = mock(AppSettingDao.class);
        manager = new SettingsManager(appSettingDao);

        when(appSettingDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(appSettingDao.findByKey(any())).thenReturn(Optional.empty());
    }

    private void stored(String key, String value) {
        AppSetting setting = new AppSetting();
        setting.setKey(key);
        setting.setValue(value);
        when(appSettingDao.findByKey(key)).thenReturn(Optional.of(setting));
    }

    @Test
    void anEmptyTableFallsBackToTheConfiguredDefault() {
        assertEquals(3, manager.intValue(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, 3));
        assertEquals(new BigDecimal("0.50"),
                manager.decimalValue(SettingKeys.CANCELLATION_LATE_FEE_RATE, new BigDecimal("0.50")));
    }

    @Test
    void aStoredValueOverridesTheDefault() {
        stored(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, "7");

        assertEquals(7, manager.intValue(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, 3));
    }

    @Test
    void aCorruptStoredValueFallsBackInsteadOfCrashing() {
        stored(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, "üç gün");

        assertEquals(3, manager.intValue(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, 3));
    }

    @Test
    void onlyDeclaredKeysCanBeWritten() {
        assertThrows(ValidationException.class, () -> manager.update("rastgele.anahtar", "5"));
        verify(appSettingDao, never()).save(any());
    }

    @Test
    void aNonNumericValueIsRejected() {
        assertThrows(ValidationException.class,
                () -> manager.update(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, "yedi"));
    }

    @Test
    void aBlankValueIsRejected() {
        assertThrows(ValidationException.class,
                () -> manager.update(SettingKeys.CANCELLATION_LATE_FEE_RATE, "   "));
    }

    @Test
    void writingCreatesTheRowWhenItIsMissing() {
        AppSetting saved = manager.update(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, "10");

        assertEquals(SettingKeys.AUTO_COMPLETION_GRACE_DAYS, saved.getKey());
        assertEquals("10", saved.getValue());
    }

    @Test
    void aDecimalRateIsAccepted() {
        assertEquals("0.35", manager.update(SettingKeys.CANCELLATION_LATE_FEE_RATE, " 0.35 ").getValue());
    }
}
