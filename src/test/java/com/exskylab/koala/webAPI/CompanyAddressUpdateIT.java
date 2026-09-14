package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionResponseDto;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.PaymentFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompanyAddressUpdateIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private IyzicoPaymentService iyzicoPaymentService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID otherDistrictId = UUID.randomUUID();
    private final UUID companyAddressId = UUID.randomUUID();
    private final UUID jobAddressId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    private PaymentFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new PaymentFixtures(jdbc);
        for (UUID id : List.of(adminId, staffId, workerId)) {
            jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                            + "email_verified, phone_verified, identity_verified, iyzico_submerchant_key) "
                            + "values (?, now(), false, ?, 'test', false, true, false, false, ?)",
                    id, id + "@example.com", "submerchant-" + id);
        }
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                otherDistrictId, "Beşiktaş", cityId);
        insertAddress(companyAddressId, "Caferağa Mah.", "Moda Cad.", "7", "34710");
        insertAddress(jobAddressId, "Osmanağa Mah.", "Söğütlüçeşme Cad.", "12", "34714");
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, phone_number, "
                        + "approval_status, address_id) values (?, now(), false, ?, ?, ?, ?, 'APPROVED', ?)",
                companyId, "Adres Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                companyId + "@example.com", "+905550000000", companyAddressId);
        insertContact(adminId, "ADMIN");
        insertContact(staffId, "STAFF");

        fixtures.seedAssignment(jobId, assignmentId, workerId, "CONFIRMED", "PENDING_COLLECTION");
        jdbc.update("update jobs set employer_type = 'COMPANY', company_id = ?, creator_id = ?, address_id = ?, "
                + "pricing_version = ?, currency = 'TRY', worker_payout = 1000.00, commission_rate = 0.0750, "
                + "commission_amount = 75.00, total_charged = 1075.00 where id = ?",
                companyId, adminId, jobAddressId, PaymentFixtures.PRICING_VERSION, jobId);

        PaymentSessionResponseDto session = new PaymentSessionResponseDto();
        session.setStatus("success");
        session.setToken("token-" + assignmentId);
        session.setPaymentPageUrl("https://sandbox.example/pay");
        when(iyzicoPaymentService.initiateCheckoutFormPayment(any(PaymentSessionRequestDto.class))).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp(jobId, assignmentId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from addresses where district_id in (?, ?)", districtId, otherDistrictId);
        jdbc.update("delete from districts where id in (?, ?)", districtId, otherDistrictId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", adminId, staffId, workerId);
        jdbc.update("delete from users where id in (?, ?, ?)", adminId, staffId, workerId);
    }

    @Test
    void theCompanyAdminReplacesTheAddressAndTheOldRowIsSoftDeleted() throws Exception {
        patchCompany(adminId, "{\"address\":" + newAddressJson(otherDistrictId) + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.districtId").value(otherDistrictId.toString()))
                .andExpect(jsonPath("$.data.address.districtName").value("Beşiktaş"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Barbaros Bulvarı"))
                .andExpect(jsonPath("$.data.address.components.postalCode").value("34353"));

        UUID currentAddressId = currentAddressId();
        assertNotEquals(companyAddressId, currentAddressId, "Şirket yeni bir adres satırına bağlanmalı");
        assertTrue(isDeleted(companyAddressId), "Eski adres satırı soft-delete edilmeli");
        assertFalse(isDeleted(currentAddressId), "Yeni adres satırı silinmiş olmamalı");
        assertFalse(isDeleted(jobAddressId), "İlanın kendi adresi dokunulmamalı");

        mockMvc.perform(get("/companies/{id}", companyId).with(user(principal(staffId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.id").value(currentAddressId.toString()))
                .andExpect(jsonPath("$.data.address.districtName").value("Beşiktaş"));
    }

    @Test
    void aStaffContactGetsTheSameNotFoundAsBeforeAndTheAddressStays() throws Exception {
        patchCompany(staffId, "{\"address\":" + newAddressJson(otherDistrictId) + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_ASSOCIATED_WITH_COMPANY"));

        assertEquals(companyAddressId, currentAddressId());
        assertFalse(isDeleted(companyAddressId));
    }

    @Test
    void anAddressWithoutADistrictIsRefusedWithAFieldError() throws Exception {
        patchCompany(adminId, "{\"address\":" + newAddressJson(null) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'address.districtId')].message").isNotEmpty());

        assertEquals(companyAddressId, currentAddressId());
        assertFalse(isDeleted(companyAddressId));
    }

    @Test
    void aPatchWithoutAnAddressLeavesTheAddressAlone() throws Exception {
        patchCompany(adminId, "{\"name\":\"Adres Şirketi Yeni Ad\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Adres Şirketi Yeni Ad"))
                .andExpect(jsonPath("$.data.address.id").value(companyAddressId.toString()))
                .andExpect(jsonPath("$.data.address.districtName").value("Kadıköy"));

        assertEquals(companyAddressId, currentAddressId());
        assertFalse(isDeleted(companyAddressId));
    }

    @Test
    void thePaymentSessionAfterTheUpdateBillsTheNewAddress() throws Exception {
        patchCompany(adminId, "{\"address\":" + newAddressJson(otherDistrictId) + "}")
                .andExpect(status().isOk());

        mockMvc.perform(post("/job-assignments/{id}/payment-session", assignmentId)
                        .with(user(principal(adminId))))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.paymentPageUrl").value("https://sandbox.example/pay"));

        ArgumentCaptor<PaymentSessionRequestDto> captor = ArgumentCaptor.forClass(PaymentSessionRequestDto.class);
        verify(iyzicoPaymentService).initiateCheckoutFormPayment(captor.capture());
        var billing = captor.getValue().getBillingAddress();
        assertTrue(billing.getAddress().contains("Barbaros Bulvarı"), billing.getAddress());
        assertTrue(billing.getAddress().contains("Beşiktaş"), billing.getAddress());
        assertFalse(billing.getAddress().contains("Moda Cad."), billing.getAddress());
        assertEquals("34353", billing.getZipCode());
        assertEquals("İstanbul", billing.getCity());
        assertTrue(captor.getValue().getBuyer().getRegistrationAddress().contains("Barbaros Bulvarı"));

        var shipping = captor.getValue().getShippingAddress();
        assertTrue(shipping.getAddress().contains("Söğütlüçeşme Cad."), shipping.getAddress());
    }

    private ResultActions patchCompany(UUID actorId, String body) throws Exception {
        return mockMvc.perform(patch("/companies/{id}", companyId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String newAddressJson(UUID district) {
        String districtField = district == null ? "" : "\"districtId\":\"" + district + "\",";
        return "{\"countryId\":\"" + countryId + "\",\"cityId\":\"" + cityId + "\"," + districtField
                + "\"components\":{\"neighborhood\":\"Yıldız Mah.\",\"thoroughfare\":\"Barbaros Bulvarı\","
                + "\"buildingNumber\":\"45\",\"postalCode\":\"34353\"},"
                + "\"position\":{\"latitude\":41.0480,\"longitude\":29.0090,\"source\":\"USER_CONFIRMED_PIN\"}}";
    }

    private void insertAddress(UUID id, String neighborhood, String thoroughfare, String buildingNumber,
                               String postalCode) {
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, postal_code, latitude, longitude, "
                        + "canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, ?, ?, 40.987654, 29.034567, 1, false)",
                id, countryId, cityId, districtId, neighborhood, thoroughfare, buildingNumber, postalCode);
    }

    private void insertContact(UUID userId, String role) {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                + "values (?, now(), false, ?, ?, ?)", UUID.randomUUID(), companyId, userId, role);
    }

    private UUID currentAddressId() {
        return jdbc.queryForObject("select address_id from companies where id = ?", UUID.class, companyId);
    }

    private boolean isDeleted(UUID addressId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select is_deleted from addresses where id = ?",
                Boolean.class, addressId));
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
