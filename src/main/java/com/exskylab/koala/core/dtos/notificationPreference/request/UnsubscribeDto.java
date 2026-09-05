package com.exskylab.koala.core.dtos.notificationPreference.request;

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
public class UnsubscribeDto {

    @NotBlank
    @Size(max = 2048)
    private String token;
}
