package com.exskylab.koala.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutAuthorizationSurfaceTest {

    private static final List<Path> MONEY_MOVING_SOURCES = List.of(
            Path.of("src/main/java/com/exskylab/koala/business/abstracts/PayoutService.java"),
            Path.of("src/main/java/com/exskylab/koala/business/concretes/PayoutManager.java"));

    @Test
    void moneyMovingMethodsCarryNoAuthorisationAnnotation() throws IOException {
        for (Path source : MONEY_MOVING_SOURCES) {
            String code = Files.readString(source);

            assertFalse(code.contains("@RequiresPermission"),
                    source + " yetki anotasyonu taşıyamaz: otomatik tamamlama zamanlayıcısı boş bir "
                            + "SecurityContext ile, iptaller ve uyuşmazlık çözümü kullanıcı kimliğiyle çağırıyor");
            assertFalse(code.contains("@PreAuthorize"), source + " @PreAuthorize kullanamaz");
        }
    }

    @Test
    void theStaffSideGoesThroughItsOwnFacade() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/com/exskylab/koala/webAPI/PayoutsController.java"));

        assertTrue(controller.contains("staffPayoutService.releaseToWorker("),
                "/admin release ucu StaffPayoutService üzerinden geçmeli");
        assertTrue(controller.contains("staffPayoutService.refundEmployer("),
                "/admin refund ucu StaffPayoutService üzerinden geçmeli");
        assertFalse(controller.contains("payoutService.releaseToWorker("),
                "/admin release ucu PayoutService'i doğrudan çağıramaz");
        assertFalse(controller.contains("payoutService.refundEmployer("),
                "/admin refund ucu PayoutService'i doğrudan çağıramaz");
        assertTrue(controller.contains("staffPayoutService.listForStaff(actor,"),
                "/admin/payouts kuyruğu aktörü StaffPayoutService'e vermeli");
        assertFalse(controller.contains("payoutService.listForStaff("),
                "/admin/payouts kuyruğu PayoutService'i doğrudan çağıramaz");
    }
}
