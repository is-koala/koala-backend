package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.CompanyType;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompanyApprovalIT extends AbstractStaffIntegrationTest {

    private static final String OPERATOR_EMAIL = "company-approval-operator@koala.test";
    private static final String FOUNDER_EMAIL = "company-approval-founder@koala.test";
    private static final String OUTSIDER_EMAIL = "company-approval-outsider@koala.test";
    private static final String TAX_PREFIX = "6200";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private CompanyDao companyDao;

    @Autowired
    private NotificationDao notificationDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User operator;
    private User founder;
    private User outsider;

    private String operatorToken;

    private String operatorToken() {
        return operatorToken;
    }

    @BeforeEach
    void seedTheQueue() {
        operator = userOf(OPERATOR_EMAIL, "Deniz", "Yönetici", Role.ROLE_ADMIN);
        founder = userOf(FOUNDER_EMAIL, "Elif", "Kurucu", Role.ROLE_USER);
        outsider = userOf(OUTSIDER_EMAIL, "Kerem", "Yabancı", Role.ROLE_USER);

        jdbcTemplate.update("delete from company_contacts where company_id in "
                + "(select id from companies where tax_number like ?)", TAX_PREFIX + "%");
        jdbcTemplate.update("delete from companies where tax_number like ?", TAX_PREFIX + "%");
        jdbcTemplate.update("delete from notifications where recipient_id = ?", founder.getId());
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'company-approval-%@iskoala.com')");
        jdbcTemplate.update("delete from staff_members where email like 'company-approval-%@iskoala.com'");

        operatorToken = staffTokenFor("company-approval-" + UUID.randomUUID() + "@iskoala.com",
                "Şirket Onaylayıcısı", "COMPANY_APPROVER");
    }

    private User userOf(String email, String firstName, String lastName, Role role) {
        return userDao.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setEmailVerified(true);
            user.setAuthorities(Set.of(role));
            return userDao.saveAndFlush(user);
        });
    }

    private Company pendingCompany(String name, String taxNumber) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.from(founder, UUID.randomUUID()), null, List.of()));
        try {
            Company company = new Company();
            company.setName(name);
            company.setEmail("basvuru@" + taxNumber + ".ornek.test");
            company.setPhoneNumber("+900000000000");
            company.setTaxNumber(taxNumber);
            company.setType(CompanyType.LIMITED);
            company.setApprovalStatus(CompanyApprovalStatus.PENDING);

            CompanyContact contact = new CompanyContact();
            contact.setUser(founder);
            contact.setRole(CompanyContactRole.ADMIN);
            company.addContact(contact);

            return companyDao.saveAndFlush(company);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ResultActions asOperator(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                                             request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + operatorToken()));
    }

    private ResultActions reject(UUID companyId, String body) throws Exception {
        return asOperator(post("/admin/companies/" + companyId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void anOperatorRejectsAnApplicationWithAReasonAndItLeavesTheQueue() throws Exception {
        Company company = pendingCompany("Yalancı Kahve Ltd.", "6200000001");

        reject(company.getId(), "{\"reason\":\"Vergi levhası okunmuyor\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.approvalNote").value("Vergi levhası okunmuyor"))
                .andExpect(jsonPath("$.data.approvalDecidedAt").isNotEmpty());

        asOperator(get("/admin/companies").param("status", "PENDING").param("query", "6200000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        asOperator(get("/admin/companies").param("status", "REJECTED").param("query", "6200000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].approvalNote").value("Vergi levhası okunmuyor"));
    }

    @Test
    void aRejectionWithoutAReasonIsRefused() throws Exception {
        Company company = pendingCompany("Uydurma Fırın AŞ", "6200000002");

        reject(company.getId(), "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        reject(company.getId(), "{}").andExpect(status().isBadRequest());

        assertEquals(CompanyApprovalStatus.PENDING,
                companyDao.findById(company.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void anApprovedCompanyCannotBeRejected() throws Exception {
        Company company = pendingCompany("Onaylı Halı Ltd.", "6200000003");

        asOperator(post("/admin/companies/" + company.getId() + "/approve"))
                .andExpect(status().isOk());

        reject(company.getId(), "{\"reason\":\"Sonradan şüphelendik\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void approvalStillWorksWithNoBodyAtAllAndAcceptsANoteWhenGiven() throws Exception {
        Company noBody = pendingCompany("Gövdesiz Lokanta Ltd.", "6200000004");
        Company withNote = pendingCompany("Gerekçeli Tesisat AŞ", "6200000005");

        asOperator(post("/admin/companies/" + noBody.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        asOperator(post("/admin/companies/" + withNote.getId() + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Vergi levhası doğrulandı\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalNote").value("Vergi levhası doğrulandı"));
    }

    @Test
    void theQueueIsSearchableByNameAndByTaxNumber() throws Exception {
        Company kahve = pendingCompany("Yalancı Kahve Ltd.", "6200000006");
        pendingCompany("Uydurma Fırın AŞ", "6200000007");

        asOperator(get("/admin/companies").param("query", "yalanci"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        asOperator(get("/admin/companies").param("query", "kahve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(kahve.getId().toString()));

        asOperator(get("/admin/companies").param("query", "6200000006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].taxNumber").value("6200000006"));

        asOperator(get("/admin/companies").param("query", TAX_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void everyQueueRowCarriesWhenItArrivedAndWhoFiledIt() throws Exception {
        pendingCompany("Kâğıtçı Matbaa Ltd.", "6200000008");

        asOperator(get("/admin/companies").param("query", "6200000008"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].createdBy.id").value(founder.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].createdBy.firstName").value("Elif"))
                .andExpect(jsonPath("$.data.content[0].taxNumber").value("6200000008"));
    }

    @Test
    void anUnknownSortFieldIsRefusedRatherThanCrashing() throws Exception {
        asOperator(get("/admin/companies").param("sort", "taxNumber,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownStatusIsRefusedRatherThanCrashing() throws Exception {
        asOperator(get("/admin/companies").param("status", "MAYBE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bothDecisionsReachTheCompanyAdminInTheirFeed() throws Exception {
        Company rejected = pendingCompany("Reddedilen Ltd.", "6200000009");
        Company approved = pendingCompany("Onaylanan AŞ", "6200000010");

        reject(rejected.getId(), "{\"reason\":\"Vergi levhası okunmuyor\"}").andExpect(status().isOk());
        asOperator(post("/admin/companies/" + approved.getId() + "/approve")).andExpect(status().isOk());

        List<String> bodies = notificationDao.findAll().stream()
                .filter(notification -> notification.getChannel() == NotificationChannel.IN_APP)
                .filter(notification -> notification.getRecipient() != null
                        && notification.getRecipient().getId().equals(founder.getId()))
                .map(notification -> notification.getSubject() + "|" + notification.getBody())
                .toList();

        assertEquals(2, bodies.size(), "iki karar da akışa düşmeliydi: " + bodies);
        assertTrue(bodies.stream().anyMatch(entry -> entry.startsWith("Reddedilen Ltd.|")), bodies.toString());
        assertTrue(bodies.stream().anyMatch(entry -> entry.startsWith("Onaylanan AŞ|")), bodies.toString());
        assertEquals(2, bodies.stream().distinct().count(), "onay ve ret aynı cümleyi taşıyor: " + bodies);
    }

    @Test
    void onlyTheCompanysOwnPeopleReadTheRejectionReason() throws Exception {
        Company company = pendingCompany("Gerekçeli Kuru Temizleme Ltd.", "6200000011");
        reject(company.getId(), "{\"reason\":\"Vergi levhası okunmuyor\"}").andExpect(status().isOk());

        mockMvc.perform(get("/companies/" + company.getId())
                        .with(user(UserPrincipal.from(founder, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.approvalNote").value("Vergi levhası okunmuyor"));

        mockMvc.perform(get("/companies/" + company.getId())
                        .with(user(UserPrincipal.from(outsider, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.approvalNote").doesNotExist());
    }
}
