package com.exskylab.koala.core.dtos.bankAccount.request;

import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.helpers.IbanValidator;
import jakarta.validation.constraints.AssertTrue;
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
    private String iban;

    @AssertTrue(message = UserMessages.INVALID_IBAN)
    public boolean isIbanValid() {
        return IbanValidator.isValid(iban);
    }
}
