package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompanyInvitationDeliveryIT extends AbstractIntegrationTest {

    private static final String INVITEE_TC = "10000000146";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private NotificationProducerService notificationProducerService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID companyAdminId = UUID.randomUUID();
    private final UUID inviteeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(companyAdminId, null);
        insertUser(inviteeId, INVITEE_TC);

        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Davet Kanali " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com");

        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, companyAdminId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from notifications where recipient_id in (?, ?) or sender_id in (?, ?) "
                        + "or created_by_id in (?, ?)",
                companyAdminId, inviteeId, companyAdminId, inviteeId, companyAdminId, inviteeId);
        jdbc.update("delete from company_contact_invitations where company_id = ?", companyId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?)", companyAdminId, inviteeId);
        jdbc.update("delete from users where id in (?, ?)", companyAdminId, inviteeId);
    }

    @Test
    void theInvitedUserFindsTheInvitationInTheFeedAndOpensItWithTheTargetId() throws Exception {
        invite().andExpect(status().isCreated());

        String feed = mockMvc.perform(get("/users/me/notifications")
                        .with(user(principal(inviteeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.content[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$.data.notifications.content[0].targetType")
                        .value("COMPANY_CONTACT_INVITATION"))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String targetId = JsonPath.read(feed, "$.data.notifications.content[0].targetId");
        assertEquals(storedInvitationId().toString(), targetId);

        mockMvc.perform(get("/contact-invitations/{invitationId}", targetId)
                        .with(user(principal(inviteeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(companyId.toString()))
                .andExpect(jsonPath("$.data.role").value("STAFF"));
    }

    @Test
    void theInvitationEmailAndTheFeedEntryAreBothRecordedWithASubject() throws Exception {
        invite().andExpect(status().isCreated());

        String emailSubject = jdbc.queryForObject(
                "select subject from notifications where recipient_id = ? and channel = 'EMAIL'",
                String.class, inviteeId);
        String feedSubject = jdbc.queryForObject(
                "select subject from notifications where recipient_id = ? and channel = 'IN_APP'",
                String.class, inviteeId);

        assertNotNull(emailSubject);
        assertEquals("Bir şirkete yetkili olarak davet edildiniz", emailSubject);
        assertEquals("Davet Kanali " + companyId, feedSubject);
    }

    private ResultActions invite() throws Exception {
        return mockMvc.perform(post("/companies/{companyId}/contact-invitations", companyId)
                .with(user(principal(companyAdminId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email(inviteeId) + "\",\"tcIdentityNumber\":\"" + INVITEE_TC
                        + "\",\"role\":\"STAFF\"}"));
    }

    private UUID storedInvitationId() {
        return jdbc.queryForObject("select id from company_contact_invitations where company_id = ?",
                UUID.class, companyId);
    }

    private void insertUser(UUID id, String tcIdentityNumber) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified, first_name, last_name, "
                        + "tc_identity_number) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false, 'Ayse', 'Yilmaz', ?)",
                id, email(id), tcIdentityNumber);
    }

    private String email(UUID id) {
        return id + "@example.com";
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }
}
