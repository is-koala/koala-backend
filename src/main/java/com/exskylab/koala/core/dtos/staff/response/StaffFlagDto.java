package com.exskylab.koala.core.dtos.staff.response;

import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffFlagDto {

    private UUID id;

    private StaffResourceType resourceType;

    private UUID resourceId;

    private StaffFlagKind kind;

    private String label;

    private UUID setByStaffId;

    private String setByDisplayName;

    private Instant setAt;

    private Instant clearedAt;
}
