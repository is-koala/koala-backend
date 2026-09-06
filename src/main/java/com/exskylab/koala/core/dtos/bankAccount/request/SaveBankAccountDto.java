package com.exskylab.koala.core.dtos.bankAccount.request;

import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.customValidations.ValidTurkishIban;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveBankAccountDto {

    @NotBlank(message = UserMessages.INVALID_IBAN)
    @ValidTurkishIban
    private String iban;
}
