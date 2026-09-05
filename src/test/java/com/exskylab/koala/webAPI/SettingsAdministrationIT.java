package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SettingsAdministrationIT extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "settings-administration-it@koala.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;

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
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from app_settings where setting_key = ?", SettingKeys.COMMISSION_RATE);
        jdbcTemplate.update("""
                update app_settings
                set setting_value = '0.50', updated_at = null, updated_by_id = null
                where setting_key = ?
                """, SettingKeys.CANCELLATION_LATE_FEE_RATE);
        jdbcTemplate.update("delete from users where email = ?", ADMIN_EMAIL);
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
        assertEquals(admin.getId(), row.get("updated_by_id"));
    }

    @Test
    void theListedSettingCarriesWhenItWasLastChanged() throws Exception {
        writeRate(SettingKeys.COMMISSION_RATE, "0.10").andExpect(status().isOk());

        mockMvc.perform(get("/admin/settings").with(user(UserPrincipal.from(admin, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key == '" + SettingKeys.COMMISSION_RATE + "')].updatedAt")
                        .isNotEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions writeRate(String key, String value)
            throws Exception {
        return mockMvc.perform(put("/admin/settings/" + key)
                .with(user(UserPrincipal.from(admin, UUID.randomUUID())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"" + value + "\"}"));
    }
}
