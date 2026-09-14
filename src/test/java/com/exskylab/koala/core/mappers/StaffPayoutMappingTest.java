package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class StaffPayoutMappingTest {

    private static final String FULL_IBAN = "TR180006200119000006672315";

    private final ObjectMapper json = new ObjectMapper();

    private PayoutMapper mapper;

    private final UUID payoutId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties();
        storage.setPublicUrl("https://cdn.example.com");
        MediaUrlResolver urls = new MediaUrlResolver(new StorageUrlResolver(storage,
                new AppProperties("http://localhost:8080", "http://localhost:3000")));
        UserMapper userMapper = new UserMapperImpl(urls, new AddressMapperImpl(),
                new EducationMapperImpl(), new ExperienceMapperImpl());

        mapper = new PayoutMapperImpl(userMapper);
    }

    private User worker() {
        User worker = new User();
        worker.setId(workerId);
        worker.setFirstName("Ayşe");
        worker.setLastName("Deneme");
        return worker;
    }

    private Payout processingPayout(BankAccount destination) {
        User worker = worker();

        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);

        Payout payout = new Payout();
        payout.setId(payoutId);
        payout.setJobAssignment(assignment);
        payout.setWorker(worker);
        payout.setBankAccount(destination);
        payout.setAmount(new BigDecimal("1000.00"));
        payout.setCurrency("TRY");
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setProviderReference("iyz-transaction-4711");
        payout.setProcessingAt(Instant.parse("2026-09-10T08:00:00Z"));
        return payout;
    }

    private BankAccount account(String iban) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setIban(iban);
        account.setAccountHolderName("Ayşe Deneme");
        return account;
    }

    @Test
    void theQueueRowCarriesTheEvidenceAnOperatorMatchesAgainstTheStatement() {
        StaffPayoutDto row = mapper.toStaffPayoutDto(processingPayout(account(FULL_IBAN)), worker());

        assertEquals(payoutId, row.id());
        assertEquals(assignmentId, row.jobAssignmentId());
        assertEquals("iyz-transaction-4711", row.providerReference());
        assertEquals("1000.00", row.amount());
        assertEquals("TRY", row.currency());
        assertEquals(PayoutStatus.PROCESSING, row.status());
        assertEquals(workerId, row.recipient().getId());
        assertEquals("Ayşe", row.recipient().getFirstName());
        assertEquals("Deneme", row.recipient().getLastName());
        assertEquals(Instant.parse("2026-09-10T08:00:00Z"), row.processingAt());
    }

    @Test
    void theIbanKeepsOnlyItsPrefixAndSuffixOnTheQueueRow() {
        StaffPayoutDto row = mapper.toStaffPayoutDto(processingPayout(account(FULL_IBAN)), worker());

        assertEquals("TR18***2315", row.maskedIban());
        String serialized = json.writeValueAsString(row);
        assertFalse(serialized.contains(FULL_IBAN), "tam IBAN yanıt gövdesine sızdı: " + serialized);
        assertFalse(serialized.contains("620011900000"), "IBAN gövdesi yanıta sızdı: " + serialized);
        assertFalse(row.toString().contains(FULL_IBAN), "tam IBAN toString üzerinden loga sızabilir");
    }

    @Test
    void aPayoutWithoutADestinationAccountRendersNoIbanAtAll() {
        StaffPayoutDto row = mapper.toStaffPayoutDto(processingPayout(null), worker());

        assertNull(row.maskedIban());
        assertEquals("iyz-transaction-4711", row.providerReference());
    }

    @Test
    void amountStaysAPlainDecimalStringOnTheQueueRow() {
        Payout payout = processingPayout(account(FULL_IBAN));
        payout.setAmount(new BigDecimal("1E+10"));

        assertEquals("10000000000", mapper.toStaffPayoutDto(payout, worker()).amount());
    }

    @Test
    void theStaffRowContractIsPinnedFieldByField() {
        JsonNode row = json.valueToTree(mapper.toStaffPayoutDto(processingPayout(account(FULL_IBAN)), worker()));

        assertEquals(Set.of("id", "jobAssignmentId", "recipient", "maskedIban", "providerReference",
                        "settlementReference", "amount", "currency", "status", "failureReason",
                        "processingAt", "completedAt", "createdAt"),
                fieldNames(row));
        assertEquals(Set.of("id", "firstName", "lastName", "username", "profilePictureUrl"),
                fieldNames(row.get("recipient")));
        assertEquals("1000.00", row.get("amount").asText());
        assertEquals("TRY", row.get("currency").asText());
    }

    @Test
    void theWorkerFacingPayoutCarriesItsDestinationAndProviderReferenceButNeverTheStatementLine() {
        Payout payout = processingPayout(account(FULL_IBAN));
        payout.setSettlementReference("stl-2026-09-14-0001");
        PayoutDto mine = mapper.toPayoutDto(payout);
        JsonNode row = json.valueToTree(mine);

        assertEquals(Set.of("id", "jobAssignmentId", "jobTitle", "companyName", "maskedIban", "bankAccountId",
                "providerReference", "amount", "currency", "status", "failureReason", "releasedAt", "settledAt",
                "completedAt", "createdAt"), fieldNames(row));
        assertEquals("TR18***2315", mine.getMaskedIban());
        assertEquals(payout.getBankAccount().getId(), mine.getBankAccountId());
        assertEquals("iyz-transaction-4711", mine.getProviderReference());
        String serialized = json.writeValueAsString(mine);
        assertFalse(serialized.contains(FULL_IBAN), "tam IBAN işçiye açılan DTO'ya sızdı: " + serialized);
        assertFalse(serialized.contains("620011900000"), "IBAN gövdesi işçiye açılan DTO'ya sızdı: " + serialized);
        assertFalse(serialized.contains("stl-2026"), "ekstre referansı işçiye açılan DTO'ya sızdı: " + serialized);
        assertFalse(serialized.contains("Ayşe"), "alıcı kimliği işçiye açılan DTO'ya sızdı: " + serialized);
    }

    @Test
    void theWorkerFacingPayoutWithoutADestinationAccountRendersNullsNotPlaceholders() {
        Payout payout = processingPayout(null);
        payout.setProviderReference(null);
        PayoutDto mine = mapper.toPayoutDto(payout);

        assertNull(mine.getMaskedIban());
        assertNull(mine.getBankAccountId());
        assertNull(mine.getProviderReference());
        assertNull(mine.getSettledAt());
    }

    @Test
    void releasedAtMirrorsCreatedAtAndSettledAtMirrorsCompletedAt() {
        Payout payout = processingPayout(account(FULL_IBAN));
        payout.setCreatedAt(Instant.parse("2026-09-09T07:00:00Z"));
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setCompletedAt(Instant.parse("2026-09-12T10:30:00Z"));
        PayoutDto mine = mapper.toPayoutDto(payout);

        assertEquals(Instant.parse("2026-09-09T07:00:00Z"), mine.getReleasedAt());
        assertEquals(mine.getCreatedAt(), mine.getReleasedAt());
        assertEquals(Instant.parse("2026-09-12T10:30:00Z"), mine.getSettledAt());
        assertEquals(mine.getCompletedAt(), mine.getSettledAt());
    }

    @Test
    void aRecipientWhoseAccountIsGoneLeavesTheEvidenceIntactAndTheIdentityEmpty() {
        StaffPayoutDto row = mapper.toStaffPayoutDto(processingPayout(account(FULL_IBAN)), null);

        assertNull(row.recipient());
        assertEquals(payoutId, row.id());
        assertEquals("iyz-transaction-4711", row.providerReference());
        assertEquals("TR18***2315", row.maskedIban());
        assertEquals("1000.00", row.amount());
    }

    private static Set<String> fieldNames(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }
}
