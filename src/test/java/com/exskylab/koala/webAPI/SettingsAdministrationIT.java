package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.constants.SystemUser;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SettingsAdministrationIT extends AbstractStaffIntegrationTest {

    private static final String ADMIN_EMAIL = "settings-administration-it@koala.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;

    private String settingsManager;

    @BeforeEach
    void createTheAdmin() {
        admin = userDao.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            User user = new User();
            user.setFirstName("Deniz");
            user.setLastName("Yönetici");
            user.setEmail(ADMIN_EMAIL);
            user.setEmailVerified(true);
            user.setAuthorities(Set.of(Role.ROLE_ADMIN));
            return userDao.saveAndFlush(user);
        });

        settingsManager = staffTokenFor("settings-" + UUID.randomUUID() + "@iskoala.com", "Ayar Yöneticisi",
                "OWNER");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'settings-%@iskoala.com')");
        jdbcTemplate.update("delete from staff_members where email like 'settings-%@iskoala.com'");
        jdbcTemplate.update("delete from app_settings where setting_key = ?", SettingKeys.COMMISSION_RATE);
        jdbcTemplate.update("""
                update app_settings
                set setting_value = '0.50', updated_at = null, updated_by_id = null
                where setting_key = ?
                """, SettingKeys.CANCELLATION_LATE_FEE_RATE);
    }

    @Test
    void aCommissionRateAboveOneIsRefused() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "1.5").andExpect(status().isBadRequest());
    }

    @Test
    void aNegativeCommissionRateIsRefused() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "-0.1").andExpect(status().isBadRequest());
    }

    @Test
    void aCommissionRateInsideTheRangeIsAccepted() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "0.10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("0.10"));
    }

    @Test
    void theCancellationLateFeeRateObeysTheSameRange() throws Exception {
        writeRate(SettingKeys.CANCELLATION_LATE_FEE_RATE, "1.5").andExpect(status().isBadRequest());
        writeRate(SettingKeys.CANCELLATION_LATE_FEE_RATE, "-0.1").andExpect(status().isBadRequest());
        writeRate(SettingKeys.CANCELLATION_LATE_FEE_RATE, "0.90").andExpect(status().isOk());
    }

    @Test
    void theAdminWhoChangedTheSettingIsRecordedOnTheRow() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "0.10").andExpect(status().isOk());
        writeRate(SettingKeys.COMMISSION_RATE, "0.12").andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select created_at, updated_at, updated_by_id from app_settings where setting_key = ?",
                SettingKeys.COMMISSION_RATE);

        assertNotNull(row.get("created_at"), "created_at bos kalmis.");
        assertNotNull(row.get("updated_at"), "updated_at bos kalmis.");
        assertEquals(SystemUser.ID, row.get("updated_by_id"),
                "Personel yolunda users'a yazacak bir aktör yok; kim değiştirdiği admin denetim kaydında (§2av)");
    }

    @Test
    void theListedSettingCarriesWhenItWasLastChanged() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "0.10").andExpect(status().isOk());

        mockMvc.perform(get("/admin/settings").header("Authorization", "Bearer " + settingsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key == '" + SettingKeys.COMMISSION_RATE + "')].updatedAt")
                        .isNotEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions writeRate(String key, String value)
            throws Exception {
        return mockMvc.perform(put("/admin/settings/" + key)
                .header("Authorization", "Bearer " + settingsManager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"" + value + "\"}"));
    }
}
