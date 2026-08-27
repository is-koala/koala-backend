package com.exskylab.koala.core.dtos.talent.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveTalentPoolEntryDto {

    private UUID companyId;

    @Size(max = 1000)
    private String note;
}
