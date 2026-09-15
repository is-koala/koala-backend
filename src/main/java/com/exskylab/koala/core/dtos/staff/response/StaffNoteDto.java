package com.exskylab.koala.core.dtos.staff.response;

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
public class StaffNoteDto {

    private UUID id;

    private StaffResourceType resourceType;

    private UUID resourceId;

    private UUID authorStaffId;

    private String authorDisplayName;

    private String body;

    private Instant createdAt;

    private Instant editedAt;
}
