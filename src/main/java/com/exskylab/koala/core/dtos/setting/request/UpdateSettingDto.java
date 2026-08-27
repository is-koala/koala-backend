package com.exskylab.koala.core.dtos.setting.request;

import com.exskylab.koala.core.constants.SettingMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSettingDto {

    @NotBlank(message = SettingMessages.VALUE_REQUIRED)
    @Size(max = 512)
    private String value;
}
