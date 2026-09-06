package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompanyContactInvitationAccessIT extends AbstractIntegrationTest {

    private static final String INVITATION_OUT_OF_REACH = "COMPANY_CONTACT_INVITATION_NOT_FOUND";
    private static final String COMPANY_OUT_OF_REACH = "USER_NOT_ASSOCIATED_WITH_COMPANY";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID companyId = UUID.randomUUID();
    private final UUID companyAdminId = UUID.randomUUID();
    private final UUID staffContactId = UUID.randomUUID();
    private final UUID inviterId = UUID.randomUUID();
    private final UUID invitedUserId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID invitationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(companyAdminId);
        insertUser(staffContactId);
        insertUser(inviterId);
        insertUser(invitedUserId);
        insertUser(outsiderId);

        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, is_approved) "
                        + "values (?, now(), false, ?, ?, ?, true)",
                companyId, "Davet Test " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com");

        insertContact(companyAdminId, "ADMIN");
        insertContact(staffContactId, "STAFF");

        jdbc.update("insert into company_contact_invitations (id, created_at, is_deleted, company_id, "
                        + "invited_by_user_id, invited_user_id, role, status, invited_at) "
                        + "values (?, now(), false, ?, ?, ?, 'STAFF', 'PENDING', now())",
                invitationId, companyId, inviterId, invitedUserId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from company_contact_invitations where company_id = ?", companyId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?, ?) "
                        + "or sender_id in (?, ?, ?, ?, ?) or created_by_id in (?, ?, ?, ?, ?)",
                companyAdminId, staffContactId, inviterId, invitedUserId, outsiderId,
                companyAdminId, staffContactId, inviterId, invitedUserId, outsiderId,
                companyAdminId, staffContactId, inviterId, invitedUserId, outsiderId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?, ?)",
                companyAdminId, staffContactId, inviterId, invitedUserId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?, ?, ?)",
                companyAdminId, staffContactId, inviterId, invitedUserId, outsiderId);
    }

    @Test
    void aCompanyAdminHoldingOnlyTheUserRoleReachesTheInvitationList() throws Exception {
        listAs(companyAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(invitationId.toString()))
                .andExpect(jsonPath("$.data[0].role").value("STAFF"));
    }

    @Test
    void aStaffContactCannotTellTheInvitationListFromAMissingCompany() throws Exception {
        listAs(staffContactId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(COMPANY_OUT_OF_REACH));

        listAs(staffContactId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(COMPANY_OUT_OF_REACH));
    }

    @Test
    void anOutsiderCannotTellTheInvitationListFromAMissingCompany() throws Exception {
        listAs(outsiderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(COMPANY_OUT_OF_REACH));

        listAs(outsiderId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(COMPANY_OUT_OF_REACH));
    }

    @Test
    void theInvitedUserTheInviterAndTheCompanyAdminAllReadTheInvitation() throws Exception {
        for (UUID reader : new UUID[]{invitedUserId, inviterId, companyAdminId}) {
            readAs(reader)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(invitationId.toString()))
                    .andExpect(jsonPath("$.data.company.id").value(companyId.toString()));
        }
    }

    @Test
    void aStrangerCannotTellTheInvitationFromAMissingOne() throws Exception {
        readAs(outsiderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));

        readAs(outsiderId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));
    }

    @Test
    void aStaffContactCannotTellTheInvitationFromAMissingOne() throws Exception {
        readAs(staffContactId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));
    }

    @Test
    void answeringSomeoneElsesInvitationLooksExactlyLikeAMissingOne() throws Exception {
        answerAs(outsiderId, invitationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));

        answerAs(outsiderId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));
    }

    @Test
    void theCompanyAdminMayReadTheInvitationButMayNotAnswerIt() throws Exception {
        readAs(companyAdminId).andExpect(status().isOk());

        answerAs(companyAdminId, invitationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(INVITATION_OUT_OF_REACH));
    }

    private ResultActions listAs(UUID actorId) throws Exception {
        return listAs(actorId, companyId);
    }

    private ResultActions listAs(UUID actorId, UUID targetCompanyId) throws Exception {
        return mockMvc.perform(get("/companies/{companyId}/contact-invitations", targetCompanyId)
                .with(user(principal(actorId))));
    }

    private ResultActions readAs(UUID actorId) throws Exception {
        return readAs(actorId, invitationId);
    }

    private ResultActions readAs(UUID actorId, UUID targetInvitationId) throws Exception {
        return mockMvc.perform(get("/contact-invitations/{invitationId}", targetInvitationId)
                .with(user(principal(actorId))));
    }

    private ResultActions answerAs(UUID actorId, UUID targetInvitationId) throws Exception {
        return mockMvc.perform(post("/contact-invitations/{invitationId}", targetInvitationId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accepted\":true}"));
    }

    private void insertContact(UUID userId, String role) {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, ?)",
                UUID.randomUUID(), companyId, userId, role);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)", id, email(id));
    }

    private String email(UUID id) {
        return id + "@example.com";
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }
}
