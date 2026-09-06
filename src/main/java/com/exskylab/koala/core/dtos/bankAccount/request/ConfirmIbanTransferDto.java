package com.exskylab.koala.core.dtos.bankAccount.request;

import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.customValidations.ValidTurkishIban;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmIbanTransferDto {

    @NotBlank(message = UserMessages.INVALID_IBAN)
    @ValidTurkishIban
    private String senderIban;

    @NotNull(message = BankAccountMessages.VERIFICATION_AMOUNT_REQUIRED)
    @Digits(integer = 17, fraction = 2, message = BankAccountMessages.VERIFICATION_AMOUNT_REQUIRED)
    @DecimalMin(value = "0.01", message = BankAccountMessages.VERIFICATION_AMOUNT_REQUIRED)
    private BigDecimal amount;

    @Size(max = 255, message = BankAccountMessages.VERIFICATION_SENDER_NAME_TOO_LONG)
    private String senderName;
}
