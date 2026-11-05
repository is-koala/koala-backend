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
import com.exskylab.koala.support.QueryCounter;
import jakarta.persistence.EntityManagerFactory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
        jdbcTemplate.update("delete from company_images where company_id in "
                + "(select id from companies where tax_number like ?)", TAX_PREFIX + "%");
        jdbcTemplate.update("delete from companies where tax_number like ?", TAX_PREFIX + "%");
        jdbcTemplate.update("delete from notifications where recipient_id = ?", founder.getId());
        jdbcTemplate.update("delete from admin_audit_entries");
        deleteStaffMembersLike("company-approval-%@iskoala.com");

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
    void theReviewCarriesContactsJobCountProfileFieldsAndTheDecidingStaffMember() throws Exception {
        Company company = pendingCompany("Yetkilili Nakliyat AŞ", "6200000014");
        jdbcTemplate.update("update companies set website = 'https://nakliyat.example', "
                + "description = 'Şehir içi nakliye' where id = ?", company.getId());
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, company_id, title, status, employer_type,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, ?, 'Şoför', 'OPEN', 'COMPANY', false, true, false, 18, 65)
                """, jobId, founder.getId(), company.getId());
        UUID operatorStaffId = jdbcTemplate.queryForObject(
                "select id from staff_members where email like 'company-approval-%@iskoala.com' limit 1", UUID.class);

        try {
            asOperator(get("/admin/companies").param("query", "6200000014"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].website").value("https://nakliyat.example"))
                    .andExpect(jsonPath("$.data.content[0].description").value("Şehir içi nakliye"))
                    .andExpect(jsonPath("$.data.content[0].jobCount").value(1))
                    .andExpect(jsonPath("$.data.content[0].companyContacts", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].companyContacts[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.content[0].companyContacts[0].user.id")
                            .value(founder.getId().toString()))
                    .andExpect(jsonPath("$.data.content[0].approvedByStaffId").value(nullValue()));

            asOperator(post("/admin/companies/" + company.getId() + "/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                    .andExpect(jsonPath("$.data.approvedByStaffId").value(operatorStaffId.toString()))
                    .andExpect(jsonPath("$.data.jobCount").value(1))
                    .andExpect(jsonPath("$.data.companyContacts", hasSize(1)));

            asOperator(get("/admin/companies/" + company.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.approvedByStaffId").value(operatorStaffId.toString()))
                    .andExpect(jsonPath("$.data.companyContacts[0].user.firstName").value("Elif"))
                    .andExpect(jsonPath("$.data.jobCount").value(1));
        } finally {
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    void theQueueResolvesContactsAndJobCountsWithoutAQueryPerRow() throws Exception {
        Company first = pendingCompany("Sayım Bir AŞ", "6200000015");
        Company second = pendingCompany("Sayım İki AŞ", "6200000016");
        List<UUID> jobIds = new ArrayList<>();
        for (Company company : List.of(first, second)) {
            UUID jobId = UUID.randomUUID();
            jobIds.add(jobId);
            jdbcTemplate.update("""
                    insert into jobs (id, created_at, is_deleted, creator_id, company_id, title, status, employer_type,
                                      insurance_required, address_public, experience_required, min_age, max_age)
                    values (?, now(), false, ?, ?, 'Sayım', 'OPEN', 'COMPANY', false, true, false, 18, 65)
                    """, jobId, founder.getId(), company.getId());
        }
        QueryCounter queries = QueryCounter.start(entityManagerFactory);
        try {
            queries.clear();
            asOperator(get("/admin/companies").param("query", "sayım").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].companyContacts", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].jobCount").value(1));
            long statementsForOneRow = queries.getPrepareStatementCount();

            queries.clear();
            asOperator(get("/admin/companies").param("query", "sayım").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[1].companyContacts", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[1].jobCount").value(1));
            long statementsForTwoRows = queries.getPrepareStatementCount();

            assertEquals(statementsForOneRow, statementsForTwoRows,
                    "şirket kuyruğu yetkilileri ya da ilan sayılarını satır başına sorguyla çekiyor (N+1)");
        } finally {
            queries.close();
            for (UUID jobId : jobIds) {
                jdbcTemplate.update("delete from jobs where id = ?", jobId);
            }
        }
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

    @Test
    void theQueueCarriesTheRegisteredAddressAndGalleryImageIdsNotStorageKeys() throws Exception {
        Company company = pendingCompany("Adresli Galeri Ltd.", "6200000012");
        UUID countryId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        UUID galleryRowId = UUID.randomUUID();
        jdbcTemplate.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbcTemplate.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbcTemplate.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbcTemplate.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, ?, 1, false)",
                addressId, countryId, cityId, districtId, "Moda", "Caferağa Sokak", "12");
        jdbcTemplate.update("update companies set address_id = ? where id = ?", addressId, company.getId());
        jdbcTemplate.update("insert into media (id, created_at, media_type, file_name, file_type, file_url, file_size) "
                        + "values (?, now(), 'IMAGE', 'vitrin.jpg', 'image/jpeg', ?, 2048)",
                imageId, "companies/" + imageId + ".jpg");
        jdbcTemplate.update("insert into company_images (id, created_at, is_deleted, company_id, image_id, "
                        + "sort_order, is_cover) values (?, now(), false, ?, ?, 0, true)",
                galleryRowId, company.getId(), imageId);

        asOperator(get("/admin/companies").param("query", "6200000012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].address.cityName").value("İstanbul"))
                .andExpect(jsonPath("$.data.content[0].address.displayAddress").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].galleryImageIds[0]").value(imageId.toString()))
                .andExpect(jsonPath("$.data.content[0].fileUrl").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].storageKey").doesNotExist());

        jdbcTemplate.update("delete from company_images where id = ?", galleryRowId);
        jdbcTemplate.update("delete from media where id = ?", imageId);
        jdbcTemplate.update("update companies set address_id = null where id = ?", company.getId());
        jdbcTemplate.update("delete from addresses where id = ?", addressId);
        jdbcTemplate.update("delete from districts where id = ?", districtId);
        jdbcTemplate.update("delete from cities where id = ?", cityId);
        jdbcTemplate.update("delete from countries where id = ?", countryId);
    }

    @Test
    void aMissingGalleryImageIsNotFoundAndFinanceCannotDownload() throws Exception {
        Company company = pendingCompany("İndirilemez Ltd.", "6200000013");
        UUID missingImage = UUID.randomUUID();

        asOperator(get("/admin/companies/" + company.getId() + "/images/" + missingImage + "/download"))
                .andExpect(status().isNotFound());

        String financeToken = staffTokenFor("company-approval-finance-" + UUID.randomUUID() + "@iskoala.com",
                "Finans", "FINANCE_ANALYST");
        mockMvc.perform(get("/admin/companies/" + company.getId() + "/images/" + missingImage + "/download")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }
}
