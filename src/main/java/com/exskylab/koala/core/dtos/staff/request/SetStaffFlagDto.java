package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SetStaffFlagDto {

    @NotNull(message = StaffMessages.FLAG_KIND_REQUIRED)
    private StaffFlagKind kind;

    @Size(max = StaffFlag.LABEL_MAX_LENGTH, message = StaffMessages.FLAG_LABEL_TOO_LONG)
    private String label;
}
