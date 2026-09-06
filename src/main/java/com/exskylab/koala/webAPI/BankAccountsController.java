package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.dtos.bankAccount.request.SaveBankAccountDto;
import com.exskylab.koala.core.dtos.bankAccount.response.BankAccountDto;
import com.exskylab.koala.core.mappers.BankAccountMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Banka hesapları", description = "Oturum sahibinin ödeme banka hesapları.")
@RestController
@RequestMapping("/users/me/bank-accounts")
public class BankAccountsController {

    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;

    public BankAccountsController(BankAccountService bankAccountService, BankAccountMapper bankAccountMapper) {
        this.bankAccountService = bankAccountService;
        this.bankAccountMapper = bankAccountMapper;
    }

    @Operation(summary = "Banka hesaplarımı getir",
            description = "Yalnızca oturum sahibinin etkin hesaplarını maskeli IBAN ile döndürür.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Banka hesapları listelendi"))
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<BankAccountDto>>> myBankAccounts() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                bankAccountMapper.toBankAccountDtoList(bankAccountService.getMyAccounts()),
                BankAccountMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Banka hesabı ekle",
            description = "TR IBAN doğrulamasından geçen hesabı ekler. İlk etkin hesap otomatik birincil olur; ham IBAN yanıtta dönmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Banka hesabı eklendi"),
            @ApiResponse(responseCode = "400", description = "IBAN geçersiz")
    })
    @PostMapping
    public ResponseEntity<SuccessDataResult<BankAccountDto>> addBankAccount(
            @RequestBody @Valid SaveBankAccountDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                bankAccountMapper.toBankAccountDto(bankAccountService.addMyAccount(request)),
                BankAccountMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Birincil banka hesabını seç",
            description = "Yalnızca oturum sahibinin etkin hesabını atomik olarak tek birincil hesap yapar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Birincil banka hesabı seçildi"),
            @ApiResponse(responseCode = "404", description = "Banka hesabı bulunamadı")
    })
    @PostMapping("/{accountId}/primary")
    public ResponseEntity<SuccessDataResult<BankAccountDto>> selectPrimary(@PathVariable UUID accountId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                bankAccountMapper.toBankAccountDto(bankAccountService.selectMyPrimaryAccount(accountId)),
                BankAccountMessages.PRIMARY_SELECTED, HttpStatus.OK));
    }

    @Operation(summary = "Banka hesabını sil",
            description = "Yalnızca oturum sahibinin etkin hesabını siler; başka kullanıcıya ait hesap da bulunamayan hesap gibi 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Banka hesabı silindi"),
            @ApiResponse(responseCode = "404", description = "Banka hesabı bulunamadı")
    })
    @DeleteMapping("/{accountId}")
    public ResponseEntity<SuccessResult> deleteBankAccount(@PathVariable UUID accountId) {
        bankAccountService.deleteMyAccount(accountId);
        return ResponseEntity.ok(new SuccessResult(BankAccountMessages.DELETED, HttpStatus.OK));
    }
}
