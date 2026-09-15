package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffNote;
import com.exskylab.koala.entities.StaffResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StaffNoteService {

    Page<StaffNote> listNotes(StaffResourceType resourceType, UUID resourceId, Pageable pageable);

    StaffNote createNote(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId, String body);

    StaffNote updateNote(StaffPrincipal actor, UUID noteId, String body);

    void deleteNote(StaffPrincipal actor, UUID noteId);

    List<StaffFlag> listActiveFlags(StaffResourceType resourceType, Collection<UUID> resourceIds);

    StaffFlag setFlag(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId,
                      StaffFlagKind kind, String label);

    void clearFlag(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId, StaffFlagKind kind);
}
