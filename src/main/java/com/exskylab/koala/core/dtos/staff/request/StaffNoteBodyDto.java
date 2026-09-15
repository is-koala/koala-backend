package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.entities.StaffNote;
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
public class StaffNoteBodyDto {

    @NotBlank(message = StaffMessages.NOTE_BODY_REQUIRED)
    @Size(max = StaffNote.BODY_MAX_LENGTH, message = StaffMessages.NOTE_BODY_TOO_LONG)
    private String body;
}
