package com.exskylab.koala.core.dtos.jobCategory.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobCategoryDto {

    private UUID id;

    private String name;

    private String code;

    private boolean requiresInsurance;

}
