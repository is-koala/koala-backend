package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.FinanceService;
import com.exskylab.koala.core.constants.FinanceMessages;
import com.exskylab.koala.core.dtos.finance.response.FinanceSummaryDto;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Finans özeti", description = "Toplu hacim, komisyon ve hakediş bakiyesi. Tekil atama payment.read.")
@RestController
@RequestMapping("/admin/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @Operation(summary = "Dönem finans özeti",
            description = "SUCCEEDED ödemelerin işveren hacmi, Koala hizmet bedeli ve çalışan hak edişi; "
                    + "COMPLETED hakedişler dönemli, PENDING/PROCESSING hakedişler anlık yükümlülük. "
                    + "from dahil, to hariç. İkisi de yoksa tüm geçmişten şimdiye. İki sayaç dönemden bağımsız ve "
                    + "anlıktır: stuckPaymentCount, VERIFY_PENDING olup sonraki doğrulama anı boş kalmış (zamanlayıcıdan "
                    + "düşmüş) ödeme denemeleri — /admin/payments?status=VERIFY_PENDING ile listelenir; "
                    + "overduePayoutCount, payout.settlement.overdue-after eşiğinden (varsayılan 3 gün) uzun süredir "
                    + "banka mutabakatı bekleyen PROCESSING hakedişler — koala_payout_settlement_overdue göstergesi ve "
                    + "runbook kuyruğuyla aynı sorgu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Özet döndürüldü"),
            @ApiResponse(responseCode = "400", description = "Dönem ya da para birimi geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.FINANCE_READ)
    @GetMapping("/summary")
    public ResponseEntity<SuccessDataResult<FinanceSummaryDto>> summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                financeService.summarize(from, to, currency),
                FinanceMessages.SUMMARY, HttpStatus.OK));
    }
}
